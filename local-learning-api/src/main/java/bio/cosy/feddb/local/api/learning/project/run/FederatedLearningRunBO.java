package bio.cosy.feddb.local.api.learning.project.run;

import bio.cosy.feddb.core.api.model.ModelSubDataDTO;
import bio.cosy.feddb.core.api.project.ProjectStatus;
import bio.cosy.feddb.core.api.socket.LearningClientStopRequestDTO;
import bio.cosy.feddb.core.api.socket.LearningClientSyncRequestDTO;
import bio.cosy.feddb.core.api.socket.LearningClientSyncResponseDTO;
import bio.cosy.feddb.core.api.socket.RelayCertSignResponseDTO;
import bio.cosy.feddb.core.api.socket.StartLearningClientRequestDTO;
import bio.cosy.feddb.core.execution.run.dto.StartRunDTO;
import bio.cosy.feddb.core.execution.run.types.RunStatusTypes;
import bio.cosy.feddb.local.api.learning.project.FederatedLearningProjectAO;
import bio.cosy.feddb.local.api.learning.project.FederatedLearningProjectEntity;
import bio.cosy.feddb.local.api.learning.request.FederatedLearningRequestEntity;
import bio.cosy.feddb.local.execution.attempt.ExecutionAttemptAO;
import bio.cosy.feddb.local.execution.attempt.ExecutionAttemptEntity;
import bio.cosy.feddb.local.execution.run.ExecutionRunAO;
import bio.cosy.feddb.local.execution.run.ExecutionRunEntity;
import bio.cosy.feddb.local.execution.step.ExecutionRunStepEntity;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class FederatedLearningRunBO {

    @Inject
    FederatedLearningSetupBO setupBO;

    @Inject
    FederatedLearningProgressBO progressBO;

    @Inject
    FederatedLearningCleanupBO cleanupBO;

    @Inject
    FederatedLearningProjectAO projectAO;

    @Inject
    ExecutionRunAO runAO;

    @Inject
    ExecutionAttemptAO attemptAO;

    public String approve(FederatedLearningRequestEntity request) {
        return setupBO.approve(request);
    }

    /** The platform starts the training: the app of the first step starts and waits. */
    @Transactional
    public Optional<LearningClientSyncResponseDTO> handleStartLearningRequestTransactional(StartLearningClientRequestDTO request) {
        String globalId = request.getGlobalUniqueLearningExperimentId();
        Optional<FederatedLearningProjectEntity> found = projectOf(globalId);
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

    /** The platform starts the current step on all clinics, or moves them to the next one. */
    @Transactional
    public Optional<LearningClientSyncResponseDTO> handleNextStepTransactional(LearningClientSyncRequestDTO request) {
        String globalId = request.getGlobalUniqueLearningExperimentId();
        Optional<FederatedLearningProjectEntity> found = projectOf(globalId);
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
        projectOf(request.getGlobalUniqueLearningExperimentId()).map(FederatedLearningProjectEntity::getRun).ifPresent(cleanupBO::stopTraining);
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

    @Transactional
    public void handleSignedTransactional(RelayCertSignResponseDTO response) {
        progressBO.handleSigned(response);
    }

    @Transactional
    public void retryPendingTransactional() {
        progressBO.retryPending(Instant.now());
    }


    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void reportStatusTransactional(Long attemptId, RunStatusTypes status) {
        ExecutionAttemptEntity attempt = attemptAO.findByIdWrite(attemptId).orElse(null);
        if (attempt == null || attempt.getStatus() != status) {
            return;
        }
        ExecutionRunStepEntity step = attempt.getStep();
        ExecutionRunEntity run = step.getRun();
        String globalId = projectAO.findByRunId(run.getId()).getRequest().getGlobalFLExperimentUniqueId();
        if (RunStatusTypes.isFinalStatus(status)) {
            cleanupBO.stopController(step.getFederation());
            progressBO.endStep(step, attempt);
            if (status == RunStatusTypes.FINISHED && run.getStepsInExecutionOrder().getLast() == step) {
                cleanupBO.finishTraining(run, globalId);
            } else if (status == RunStatusTypes.ERROR) {
                cleanupBO.failTraining(run, attempt.getFailure(), globalId);
            }
        }
        progressBO.reportStatus(run, step, status, globalId);
    }

    private Optional<FederatedLearningProjectEntity> projectOf(String globalId) {
        return projectAO.findByRequestIdOptional(globalId).filter(project -> project.getRun() != null);
    }

    private ExecutionRunEntity lockRun(ExecutionRunEntity run) {
        return runAO.findByIdWrite(run.getId()).orElseThrow();
    }
}
