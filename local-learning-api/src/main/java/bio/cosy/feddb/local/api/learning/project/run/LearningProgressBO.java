package bio.cosy.feddb.local.api.learning.project.run;

import bio.cosy.feddb.core.api.model.ModelSubDataDTO;
import bio.cosy.feddb.core.api.project.ProjectStatus;
import bio.cosy.feddb.core.api.socket.LearningClientSyncRequestDTO;
import bio.cosy.feddb.core.api.socket.LearningClientSyncResponseDTO;
import bio.cosy.feddb.core.execution.file.ExecutionFileDirection;
import bio.cosy.feddb.core.execution.orchestrator.WorkflowOrchestrator;
import bio.cosy.feddb.core.execution.run.dto.AppMessageWrapperDTO;
import bio.cosy.feddb.core.execution.run.dto.StartRunDTO;
import bio.cosy.feddb.core.execution.run.dto.UpdateRunDTO;
import bio.cosy.feddb.core.execution.run.types.AppMessageTypeEnum;
import bio.cosy.feddb.core.execution.run.types.AppRunTypeEnum;
import bio.cosy.feddb.core.execution.run.types.RunStatusTypes;
import bio.cosy.feddb.local.api.eam.WebsocketSender;
import bio.cosy.feddb.local.api.learning.project.LearningProjectAO;
import bio.cosy.feddb.local.api.learning.project.LearningProjectEntity;
import bio.cosy.feddb.local.api.workflow.node.WorkflowNodeEntity;
import bio.cosy.feddb.local.execution.attempt.ExecutionAttemptAO;
import bio.cosy.feddb.local.execution.attempt.ExecutionAttemptEntity;
import bio.cosy.feddb.local.execution.attempt.ExecutionAttemptLifecycle;
import bio.cosy.feddb.local.execution.federation.ExecutionFederationAO;
import bio.cosy.feddb.local.execution.file.ExecutionFileBO;
import bio.cosy.feddb.local.execution.run.ExecutionRunBO;
import bio.cosy.feddb.local.execution.run.ExecutionRunEntity;
import bio.cosy.feddb.local.execution.step.ExecutionRunStepEntity;
import bio.cosy.feddb.local.services.GlobalAPIModelResultService;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;
import org.apache.commons.io.FileUtils;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.resteasy.reactive.client.api.ClientMultipartForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import java.io.File;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class LearningProgressBO {

    @Inject
    ExecutionAttemptLifecycle lifecycle;

    @Inject
    ExecutionRunBO runBO;

    @Inject
    ExecutionAttemptAO attemptAO;

    @Inject
    ExecutionFederationAO federationAO;

    @Inject
    ExecutionFileBO executionFileBO;

    @Inject
    WorkflowOrchestrator orchestrator;

    @Inject
    LearningProjectAO projectAO;

    @Inject
    WebsocketSender websocketSender;

    @Inject
    @RestClient
    GlobalAPIModelResultService globalAPIModelResultService;

    /**
     * Starts the current step once every clinic is ready for it; an answer to the platform only when it failed.
     */
    public Optional<LearningClientSyncResponseDTO> startCurrentStep(ExecutionRunEntity run, LearningClientSyncRequestDTO request) {
        String globalId = request.getGlobalUniqueLearningExperimentId();
        // The relay data differs per clinic, every clinic gets all requests and takes its own.
        if (request.getUniqueRandomClinicId() != null && !request.getUniqueRandomClinicId().equals(run.getParticipantId())) {
            return Optional.empty();
        }
        Optional<ExecutionRunStepEntity> current = run.getCurrentStep();
        String nodeId = current.map(step -> step.getWorkflowNode().getNodeId()).orElse(null);
        if (current.isEmpty() || request.getCurrentNodeId() != null && !request.getCurrentNodeId().equals(nodeId)) {
            Log.warnf("Ignoring start of node %s of training %s, this clinic is at node %s", request.getCurrentNodeId(), globalId, nodeId);
            return Optional.empty();
        }
        ExecutionAttemptEntity attempt = current.get().getSelectedAttempt();
        if (attempt == null || attempt.getStatus() == RunStatusTypes.RUNNING || attempt.hasEnded()) {
            Log.infof("Ignoring repeated start of node %s of training %s", nodeId, globalId);
            return Optional.empty();
        }
        try {
            if (request.getRelayInfo() != null) {
                federationAO.applyRelay(current.get(), request.getRelayInfo());
            }
            runStep(current.get(), attempt);
            return Optional.empty();
        } catch (RuntimeException e) {
            Log.errorf(e, "Could not start node %s of training %s", nodeId, globalId);
            return Optional.of(LearningClientSyncResponseDTO.createErrorResponse("Error starting learning: " + e.getMessage(),
                    globalId, run.getParticipantId()));
        }
    }

    /**
     * Keeps the outputs the app of a step uploaded.
     */
    public void keepOutputs(Long attemptId, List<FileUpload> uploads) {
        attemptAO.findByIdOptional(attemptId).ifPresent(attempt -> executionFileBO.addOutputs(attempt, uploads));
    }

    /**
     * Hands the model a step trained to the platform if this clinic coordinates; a private one too, the platform refuses publishing it.
     */
    public void uploadModel(Long attemptId, ModelSubDataDTO model) {
        ExecutionAttemptEntity attempt = attemptAO.findById(attemptId);
        if (attempt == null) {
            return;
        }
        ExecutionRunEntity run = attempt.getStep().getRun();
        LearningProjectEntity project = projectAO.findByRunId(run.getId());
        if (!Boolean.TRUE.equals(project.getIsCoordinator())) {
            Log.infof("Not handing the model of attempt %d to the platform: this clinic does not coordinate", attemptId);
            return;
        }
        ClientMultipartForm form = ClientMultipartForm.create();
        form.attribute("globalFLExperimentUniqueId", project.getRequest().getGlobalExperimentUniqueId(), "experimentId");
        form.attribute("appVersionId", String.valueOf(attempt.getStep().getWorkflowNode().getFederatedAppVersionId()), "appVersionId");
        form.attribute("clinicId", run.getParticipantId(), "clinicId");
        form.binaryFileUpload("files", model.getFile().fileName(), model.getFile().uploadedFile().toString(), "application/octet-stream");
        try (Response response = globalAPIModelResultService.upload(form)) {
            if (response.getStatus() != Response.Status.CREATED.getStatusCode()) {
                Log.errorf("Platform answered %d to the model of attempt %d: %s", response.getStatus(), attemptId, response.readEntity(String.class));
            }
        } catch (RuntimeException e) {
            Log.errorf(e, "Could not hand the model of attempt %d to the platform", attemptId);
        }
    }

    /**
     * The app of the step ended; a finished step keeps what its app wrote and hands it to the next one.
     */
    public void endStep(ExecutionRunStepEntity step, ExecutionAttemptEntity attempt) {
        step.end(attempt.getStatus(), attempt.getFailure());
        if (attempt.getStatus() == RunStatusTypes.FINISHED) {
            keepWrittenOutputs(attempt);
            step.setSelectedAttempt(attempt);
        }
    }

    /**
     * The platform hears the status the app of the step reached.
     */
    public void reportStatus(ExecutionRunEntity run, ExecutionRunStepEntity step, RunStatusTypes status, String globalId) {
        websocketSender.sendRunningUpdate(step.getWorkflowNode().getNodeId(), globalId, run.getStatus(),
                ProjectStatus.fromRunStatusTypes(status), run.getParticipantId());
    }


    // An old FeatureCloud app learns through the controller only, any other app is started; a relay app needs both.
    private void runStep(ExecutionRunStepEntity step, ExecutionAttemptEntity attempt) {
        WorkflowNodeEntity node = step.getWorkflowNode();
        if (node.isSupportsFederatedLearning()) {
            startController(step, attempt);
        }
        StartRunDTO start = lifecycle.startupOfTransactional(attempt.getId());
        AppMessageWrapperDTO<StartRunDTO> message = start.isSupportFederatedLearning()
                ? new AppMessageWrapperDTO<>(AppMessageTypeEnum.START_FEDERATED_RUN, start, AppRunTypeEnum.FEDERATED_RUN)
                : new AppMessageWrapperDTO<>(AppMessageTypeEnum.START_RUN, start, AppRunTypeEnum.EXPERIMENT_RUN);
        if (!runBO.sendToApp(attempt.getId(), message)) {
            throw new IllegalStateException("App of step " + step.getId() + " is not connected");
        }
    }

    // Fails the app of a step, as if it reported the error; the platform then stops the training.
    public void failStep(ExecutionRunStepEntity step, String error) {
        ExecutionAttemptEntity attempt = step.getSelectedAttempt();
        if (attempt == null) {
            return;
        }
        lifecycle.updateTransactional(attempt.getId(), new UpdateRunDTO(attempt.getId(), RunStatusTypes.ERROR, error));
    }

    // An app that uploaded nothing left its outputs in its volume.
    private void keepWrittenOutputs(ExecutionAttemptEntity attempt) {
        if (attempt.getExecutionFiles().stream().anyMatch(file -> file.getDirection() == ExecutionFileDirection.OUTPUT)) {
            return;
        }
        List<File> written = orchestrator.filesOf(attempt.getStep().getRun().getId(), attempt.getId());
        try {
            executionFileBO.storeAndLinkFiles(attempt, written, ExecutionFileDirection.OUTPUT);
        } finally {
            written.forEach(FileUtils::deleteQuietly);
        }
    }

    //FOR FL
    public void startController(ExecutionRunStepEntity step, ExecutionAttemptEntity attempt) {
        //To nothing here
    }
}
