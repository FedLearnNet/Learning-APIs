package bio.cosy.feddb.local.api.learning.project.run;

import bio.cosy.feddb.core.api.model.ModelSubDataDTO;
import bio.cosy.feddb.core.api.project.ProjectStatus;
import bio.cosy.feddb.core.api.socket.LearningClientStopRequestDTO;
import bio.cosy.feddb.core.api.socket.LearningClientSyncRequestDTO;
import bio.cosy.feddb.core.api.socket.LearningClientSyncResponseDTO;
import bio.cosy.feddb.core.api.socket.StartLearningClientRequestDTO;
import bio.cosy.feddb.core.execution.ExecutionMode;
import bio.cosy.feddb.core.execution.run.dto.StartRunDTO;
import bio.cosy.feddb.core.execution.run.types.RunStatusTypes;
import bio.cosy.feddb.local.api.learning.project.LearningProjectAO;
import bio.cosy.feddb.local.api.learning.project.LearningProjectEntity;
import bio.cosy.feddb.local.api.learning.project.run.federated.FederatedLearningCleanupBO;
import bio.cosy.feddb.local.api.learning.request.LearningRequestEntity;
import bio.cosy.feddb.local.execution.attempt.ExecutionAttemptAO;
import bio.cosy.feddb.local.execution.attempt.ExecutionAttemptEntity;
import bio.cosy.feddb.local.execution.run.ExecutionRunAO;
import bio.cosy.feddb.local.execution.run.ExecutionRunEntity;
import bio.cosy.feddb.local.execution.step.ExecutionRunStepEntity;
import io.quarkus.logging.Log;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import java.util.List;
import java.util.Optional;

public abstract class LearningRunBO {

    @Inject
    LearningSetupBO setupBO;

    @Inject
    LearningProjectAO projectAO;

    @Inject
    ExecutionRunAO runAO;

    @Inject
    ExecutionAttemptAO attemptAO;

    @Inject
    FederatedLearningCleanupBO federatedCleanupBO;

    @Inject
    LearningProgressBO progressBO;

    @Inject
    LearningCleanupBO cleanupBO;

    public String approve(LearningRequestEntity request) {
        return setupBO.approve(request);
    }

    /**
     * The platform starts the training: the app of the first step starts and waits.
     */
    @Transactional
    public Optional<LearningClientSyncResponseDTO> handleStartLearningRequestTransactional(StartLearningClientRequestDTO request) {
        String globalId = request.getGlobalUniqueLearningExperimentId();
        Optional<LearningProjectEntity> found = projectOf(globalId);
        if (found.isEmpty()) {
            Log.errorf("No training %s to start", globalId);
            return Optional.of(LearningClientSyncResponseDTO.createErrorResponse("Experiment not found", globalId));
        }
        ExecutionRunEntity run = lockRun(found.get().getRun());
        ExecutionRunStepEntity step = setupBO.startTraining(found.get(), run, request);
        LearningClientSyncResponseDTO response = LearningClientSyncResponseDTO.createResponse(globalId, run.getParticipantId(),
                step != null ? step.getWorkflowNode().getNodeId() : null);
        response.setStepStatus(ProjectStatus.INIT);
        return Optional.of(response);
    }

    /**
     * The platform starts the current step on all clinics, or moves them to the next one.
     */
    @Transactional
    public Optional<LearningClientSyncResponseDTO> handleNextStepTransactional(LearningClientSyncRequestDTO request) {
        String globalId = request.getGlobalUniqueLearningExperimentId();
        Optional<LearningProjectEntity> found = projectOf(globalId);
        if (found.isEmpty()) {
            Log.errorf("No training %s to move on", globalId);
            return Optional.of(LearningClientSyncResponseDTO.createErrorResponse("Experiment not found", globalId));
        }
        ExecutionRunEntity run = lockRun(found.get().getRun());
        if (!request.isStartRunning()) {
            setupBO.prepareNextStep(run, request.getRelayInfo());
            return Optional.empty();
        }
        return progressBO.startCurrentStep(run, request);
    }

    @Transactional
    public void stopLearningTransactional(LearningClientStopRequestDTO request) {
        projectOf(request.getGlobalUniqueLearningExperimentId())
                .map(LearningProjectEntity::getRun).ifPresent(r ->
                        getCleanupBO(r.getMode()).stopTraining(r)
                );
    }

    public void customizeStartup(ExecutionAttemptEntity attempt, StartRunDTO start) {
        setupBO.customizeStartup(attempt, start);
    }

    public void stageInputs(ExecutionAttemptEntity attempt) {
        setupBO.stageInputs(attempt);
    }

    @Transactional
    public void keepOutputsTransactional(Long attemptId, List<FileUpload> uploads) {
        progressBO.keepOutputs(attemptId, uploads);
    }

    @Transactional
    public void uploadModelTransactional(Long attemptId, ModelSubDataDTO model) {
        progressBO.uploadModel(attemptId, model);
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void reportStatusTransactional(Long attemptId, RunStatusTypes status) {
        ExecutionAttemptEntity attempt = attemptAO.findByIdWrite(attemptId).orElse(null);
        if (attempt == null || attempt.getStatus() != status) {
            return;
        }
        ExecutionRunStepEntity step = attempt.getStep();
        ExecutionRunEntity run = step.getRun();
        String globalId = projectAO.findByRunId(run.getId()).getRequest().getGlobalExperimentUniqueId();
        if (RunStatusTypes.isFinalStatus(status)) {
            if (run.getMode().equals(ExecutionMode.FEDERATED)) {
                cleanUpFL(step);
            }
            progressBO.endStep(step, attempt);
            if (status == RunStatusTypes.FINISHED && run.getStepsInExecutionOrder().getLast() == step) {
                getCleanupBO(run.getMode()).finishTraining(run, globalId);
            } else if (status == RunStatusTypes.ERROR) {
                getCleanupBO(run.getMode()).failTraining(run, attempt.getFailure(), globalId);
            }
        }
        progressBO.reportStatus(run, step, status, globalId);
    }

    private Optional<LearningProjectEntity> projectOf(String globalId) {
        return projectAO.findByRequestIdOptional(globalId).filter(project -> project.getRun() != null);
    }

    private ExecutionRunEntity lockRun(ExecutionRunEntity run) {
        return runAO.findByIdWrite(run.getId()).orElseThrow();
    }

    public void cleanUpFL(ExecutionRunStepEntity step) {
        federatedCleanupBO.stopController(step.getFederation());
    }

    public LearningCleanupBO getCleanupBO(ExecutionMode mode) {
        if (mode.equals(ExecutionMode.FEDERATED)) {
            return federatedCleanupBO;
        }
        return cleanupBO;
    }
}
