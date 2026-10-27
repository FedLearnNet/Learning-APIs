package bio.cosy.feddb.local.api.learning.project.run;

import bio.cosy.feddb.core.api.model.ModelSubDataDTO;
import bio.cosy.feddb.core.api.project.ProjectStatus;
import bio.cosy.feddb.core.api.socket.LearningClientSyncRequestDTO;
import bio.cosy.feddb.core.api.socket.LearningClientSyncResponseDTO;
import bio.cosy.feddb.core.api.socket.RelayCertSignRequestDTO;
import bio.cosy.feddb.core.api.socket.RelayCertSignResponseDTO;
import bio.cosy.feddb.core.execution.file.ExecutionFileDirection;
import bio.cosy.feddb.core.execution.orchestrator.WorkflowOrchestrator;
import bio.cosy.feddb.core.execution.run.dto.AppMessageWrapperDTO;
import bio.cosy.feddb.core.execution.run.dto.StartRunDTO;
import bio.cosy.feddb.core.execution.run.dto.UpdateRunDTO;
import bio.cosy.feddb.core.execution.run.types.AppMessageTypeEnum;
import bio.cosy.feddb.core.execution.run.types.AppRunTypeEnum;
import bio.cosy.feddb.core.execution.run.types.RunStatusTypes;
import bio.cosy.feddb.core.services.controller.ControllerStartLearningRequestDTO;
import bio.cosy.feddb.core.services.controller.ControllerStartLearningResponseDTO;
import bio.cosy.feddb.core.services.controller.ControllerStartRelayingRequestDTO;
import bio.cosy.feddb.local.api.eam.WebsocketSender;
import bio.cosy.feddb.local.api.learning.project.FederatedLearningProjectAO;
import bio.cosy.feddb.local.api.learning.project.FederatedLearningProjectEntity;
import bio.cosy.feddb.local.api.workflow.node.WorkflowNodeEntity;
import bio.cosy.feddb.local.config.FLNetClientConfig;
import bio.cosy.feddb.local.execution.attempt.ExecutionAttemptAO;
import bio.cosy.feddb.local.execution.attempt.ExecutionAttemptEntity;
import bio.cosy.feddb.local.execution.attempt.ExecutionAttemptLifecycle;
import bio.cosy.feddb.local.execution.federation.ExecutionFederationAO;
import bio.cosy.feddb.local.execution.federation.ExecutionFederationEntity;
import bio.cosy.feddb.local.execution.file.ExecutionFileBO;
import bio.cosy.feddb.local.execution.run.ExecutionRunBO;
import bio.cosy.feddb.local.execution.run.ExecutionRunEntity;
import bio.cosy.feddb.local.execution.step.ExecutionRunStepAO;
import bio.cosy.feddb.local.execution.step.ExecutionRunStepEntity;
import bio.cosy.feddb.local.services.GlobalAPIModelResultService;
import bio.cosy.feddb.local.services.controller.LocalControllerLearningService;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.LockModeType;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import org.apache.commons.io.FileUtils;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.resteasy.reactive.client.api.ClientMultipartForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import java.io.File;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@ApplicationScoped
public class FederatedLearningProgressBO {

    @Inject
    ExecutionAttemptLifecycle lifecycle;

    @Inject
    ExecutionRunBO runBO;

    @Inject
    ExecutionAttemptAO attemptAO;

    @Inject
    ExecutionRunStepAO stepAO;

    @Inject
    ExecutionFederationAO federationAO;

    @Inject
    ExecutionFileBO executionFileBO;

    @Inject
    WorkflowOrchestrator orchestrator;

    @Inject
    FederatedLearningProjectAO projectAO;

    @Inject
    WebsocketSender websocketSender;

    @Inject
    FLNetClientConfig config;

    @Inject
    @RestClient
    LocalControllerLearningService controllerService;

    @Inject
    @RestClient
    GlobalAPIModelResultService globalAPIModelResultService;

    /** Starts the current step once every clinic is ready for it; an answer to the platform only when it failed. */
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

    /** Keeps the outputs the app of a step uploaded. */
    public void keepOutputs(Long attemptId, List<FileUpload> uploads) {
        attemptAO.findByIdOptional(attemptId).ifPresent(attempt -> executionFileBO.addOutputs(attempt, uploads));
    }

    /** Hands the model a step trained to the platform, if this clinic coordinates and the model may be public. */
    public void uploadModel(Long attemptId, ModelSubDataDTO model) {
        ExecutionAttemptEntity attempt = attemptAO.findById(attemptId);
        if (attempt == null) {
            return;
        }
        ExecutionRunEntity run = attempt.getStep().getRun();
        FederatedLearningProjectEntity project = projectAO.findByRunId(run.getId());
        if (!Boolean.TRUE.equals(project.getIsCoordinator()) || !Boolean.TRUE.equals(project.getRequest().getModelCanBePublic())) {
            Log.infof("Not handing the model of attempt %d to the platform: this clinic does not coordinate or it is private", attemptId);
            return;
        }
        ClientMultipartForm form = ClientMultipartForm.create();
        form.attribute("globalFLExperimentUniqueId", project.getRequest().getGlobalFLExperimentUniqueId(), "experimentId");
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

    /** The app of the step ended; a finished step keeps what its app wrote and hands it to the next one. */
    public void endStep(ExecutionRunStepEntity step, ExecutionAttemptEntity attempt) {
        step.end(attempt.getStatus(), attempt.getFailure());
        if (attempt.getStatus() == RunStatusTypes.FINISHED) {
            keepWrittenOutputs(attempt);
            step.setSelectedAttempt(attempt);
        }
    }

    /** The platform hears the status the app of the step reached. */
    public void reportStatus(ExecutionRunEntity run, ExecutionRunStepEntity step, RunStatusTypes status, String globalId) {
        websocketSender.sendRunningUpdate(step.getWorkflowNode().getNodeId(), globalId, run.getStatus(),
                ProjectStatus.fromRunStatusTypes(status), run.getParticipantId());
    }

    /** Stores the request of the step's controller run and asks the platform to have it signed. */
    public void requestCertificate(ExecutionFederationEntity federation, String csr) {
        federation.setRelayCsr(csr);
        federation.setRelayCertRequestedAt(Instant.now());
        federation.setRelayCertAttempts(1);
        federation.setRelayCertSigned(false);
        federation.setRelayStopped(false);
        Log.infof("Requesting relay certificate for step %d", federation.getStep().getId());
        sendCertRequest(federation);
    }

    /**
     * Hands the signed certificate to the controller, which then joins the relay. The step fails if signing was
     * refused or the controller cannot join.
     */
    public void handleSigned(RelayCertSignResponseDTO response) {
        Optional<ExecutionRunStepEntity> found = projectAO.findByRequestIdOptional(response.getGlobalUniqueExperimentId())
                .map(project -> project.getRun())
                .filter(run -> run != null && run.getParticipantId().equals(response.getUniqueRandomClinicId()))
                .flatMap(run -> stepAO.findByNodeId(run.getId(), response.getCurrentNodeId()));
        // Locked, so of two answers to a retried request only the first is used.
        ExecutionFederationEntity federation = found.map(ExecutionRunStepEntity::getFederation)
                .flatMap(linked -> federationAO.findByIdOptional(linked.getId(), LockModeType.PESSIMISTIC_WRITE))
                .orElse(null);
        if (federation == null || federation.getConfig() == null
                || !Objects.equals(federation.getConfig().getClientId(), response.getClientId())) {
            Log.warnf("Ignoring relay certificate of node %s of training %s, it is not for this clinic",
                    response.getCurrentNodeId(), response.getGlobalUniqueExperimentId());
            return;
        }
        ExecutionRunStepEntity step = federation.getStep();
        if (step.hasEnded() || Boolean.TRUE.equals(federation.getRelayStopped())
                || federation.getRelayCsr() == null || Boolean.TRUE.equals(federation.getRelayCertSigned())) {
            Log.infof("Ignoring relay certificate for step %d, it does not wait for one", step.getId());
            return;
        }
        federation.setRelayCertSigned(true);
        if (response.getCertificate() == null || response.getCertificate().isBlank()) {
            failStep(step, "Relay certificate was not signed: " + response.getError());
            return;
        }
        // The app key of a controller run is the relay client id.
        ControllerStartRelayingRequestDTO request = new ControllerStartRelayingRequestDTO(federation.getConfig().getChannel(),
                federation.getConfig().getClientId(), response.getCertificate());
        String error = null;
        try (Response controllerResponse = controllerService.startRelaying(request)) {
            if (controllerResponse.getStatus() != Response.Status.OK.getStatusCode()) {
                error = "status " + controllerResponse.getStatus();
            }
        } catch (WebApplicationException e) {
            error = "status " + e.getResponse().getStatus();
        } catch (RuntimeException e) {
            Log.errorf(e, "Could not hand the relay certificate to the controller of step %d", step.getId());
            error = e.getMessage();
        }
        if (error != null) {
            failStep(step, "Controller could not connect to the relay server: " + error);
            return;
        }
        Log.infof("Controller of step %d joined the relay", step.getId());
    }

    /** Sends a request again if its certificate did not arrive in time, and fails the step if it never does. */
    public void retryPending(Instant now) {
        long timeoutMillis = config.relay().certSign().timeout().toMillis();
        int maxAttempts = config.relay().certSign().maxAttempts();
        for (ExecutionFederationEntity pending : federationAO.listPendingRelayCerts()) {
            Instant requestedAt = pending.getRelayCertRequestedAt();
            if (requestedAt != null && now.toEpochMilli() - requestedAt.toEpochMilli() < timeoutMillis) {
                continue;
            }
            int attempts = pending.getRelayCertAttempts() != null ? pending.getRelayCertAttempts() : 0;
            if (attempts >= maxAttempts) {
                // A late answer must not connect the controller of a failed step.
                pending.setRelayCertSigned(true);
                failStep(pending.getStep(), "Relay certificate was not signed after " + attempts + " attempts");
                continue;
            }
            Log.warnf("No relay certificate for step %d yet, sending request %d of %d", pending.getStep().getId(), attempts + 1, maxAttempts);
            pending.setRelayCertAttempts(attempts + 1);
            pending.setRelayCertRequestedAt(now);
            sendCertRequest(pending);
        }
    }

    // An old FeatureCloud app learns through the controller only, any other app is started; a relay app needs both.
    private void runStep(ExecutionRunStepEntity step, ExecutionAttemptEntity attempt) {
        WorkflowNodeEntity node = step.getWorkflowNode();
        if (node.isOldFCVersion() || node.isSupportsFederatedLearning()) {
            startController(step, attempt);
        }
        if (node.isOldFCVersion()) {
            return;
        }
        StartRunDTO start = lifecycle.startupOfTransactional(attempt.getId());
        AppMessageWrapperDTO<StartRunDTO> message = start.isSupportFederatedLearning()
                ? new AppMessageWrapperDTO<>(AppMessageTypeEnum.START_FEDERATED_RUN, start, AppRunTypeEnum.FEDERATED_RUN)
                : new AppMessageWrapperDTO<>(AppMessageTypeEnum.START_RUN, start, AppRunTypeEnum.EXPERIMENT_RUN);
        if (!runBO.sendToApp(attempt.getId(), message)) {
            throw new IllegalStateException("App of step " + step.getId() + " is not connected");
        }
    }

    // The controller joins the relay once the platform had its certificate signed.
    private void startController(ExecutionRunStepEntity step, ExecutionAttemptEntity attempt) {
        ExecutionFederationEntity federation = step.getFederation();
        if (federation == null || federation.getConfig() == null) {
            throw new IllegalStateException("Step " + step.getId() + " has no relay to learn through");
        }
        String csr;
        try (Response response = controllerService.startLearning(new ControllerStartLearningRequestDTO(federation.getConfig(), attempt.getId()))) {
            if (response.getStatus() != Response.Status.OK.getStatusCode()) {
                throw new IllegalStateException("controller answered " + response.getStatus() + ": " + response.readEntity(String.class));
            }
            ControllerStartLearningResponseDTO started = response.readEntity(ControllerStartLearningResponseDTO.class);
            csr = started != null ? started.getCsr() : null;
        }
        if (csr == null || csr.isBlank()) {
            throw new IllegalStateException("controller returned no certificate signing request");
        }
        requestCertificate(federation, csr);
    }

    private void sendCertRequest(ExecutionFederationEntity federation) {
        ExecutionRunStepEntity step = federation.getStep();
        ExecutionRunEntity run = step.getRun();
        String globalId = projectAO.findByRunId(run.getId()).getRequest().getGlobalFLExperimentUniqueId();
        websocketSender.sendRelayCertRequest(new RelayCertSignRequestDTO(globalId, run.getParticipantId(),
                step.getWorkflowNode().getNodeId(), federation.getConfig().getClientId(), federation.getRelayCsr()));
    }

    // Fails the app of a step, as if it reported the error; the platform then stops the training.
    private void failStep(ExecutionRunStepEntity step, String error) {
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
}
