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
import bio.cosy.feddb.local.api.learning.project.run.federated.FederatedLearningProgressBO;
import bio.cosy.feddb.local.api.learning.request.LearningRequestEntity;
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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@ApplicationScoped
public class LearningRunBO {

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
    FederatedLearningProgressBO federatedProgressBO;

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
        return progressOf(run.getMode()).startCurrentStep(run, request);
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
        // The run stream and the finished event both report an end; a step ends once.
        if (run.getMode() == ExecutionMode.SINGLE
                || RunStatusTypes.isFinalStatus(status) && RunStatusTypes.isFinalStatus(step.getStatus())) {
            return;
        }
        String globalId = projectAO.findByRunId(run.getId()).getRequest().getGlobalExperimentUniqueId();
        if (RunStatusTypes.isFinalStatus(status)) {
            if (run.getMode().equals(ExecutionMode.FEDERATED)) {
                cleanUpFL(step);
            }
            progressOf(run.getMode()).endStep(step, attempt);
            if (status == RunStatusTypes.FINISHED && run.getStepsInExecutionOrder().getLast() == step) {
                getCleanupBO(run.getMode()).finishTraining(run, globalId);
            } else if (status == RunStatusTypes.ERROR) {
                getCleanupBO(run.getMode()).failTraining(run, attempt.getFailure(), globalId);
            }
        }
        progressBO.reportStatus(run, step, status, globalId);
    }

    /**
     * Ended attempts of open trainings whose step was never ended, e.g. a report lost before a restart.
     */
    @Transactional
    public Map<Long, RunStatusTypes> listUnhandledEndsTransactional() {
        Map<Long, RunStatusTypes> ends = new LinkedHashMap<>();
        runAO.listOpenTrainings().stream()
                .filter(run -> run.getMode() != ExecutionMode.SINGLE)
                .flatMap(run -> run.getStepsInExecutionOrder().stream())
                .filter(step -> !RunStatusTypes.isFinalStatus(step.getStatus()))
                .map(ExecutionRunStepEntity::getSelectedAttempt)
                .filter(attempt -> attempt != null && attempt.hasEnded())
                .forEach(attempt -> ends.put(attempt.getId(), attempt.getStatus()));
        return ends;
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

    private LearningProgressBO progressOf(ExecutionMode mode) {
        return mode == ExecutionMode.FEDERATED ? federatedProgressBO : progressBO;
    }

    public LearningCleanupBO getCleanupBO(ExecutionMode mode) {
        if (mode.equals(ExecutionMode.FEDERATED)) {
            return federatedCleanupBO;
        }
        return cleanupBO;
    }
}
