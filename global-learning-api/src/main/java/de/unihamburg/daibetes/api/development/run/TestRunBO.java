package de.unihamburg.daibetes.api.development.run;

import bio.cosy.feddb.core.execution.ExecutionMode;
import bio.cosy.feddb.core.api.model.ModelSubDataDTO;
import bio.cosy.feddb.core.execution.file.ExecutionFileDirection;
import bio.cosy.feddb.core.execution.message.ExecutionMessageLogDTO;
import bio.cosy.feddb.core.execution.message.ExecutionMessageMetricDTO;
import bio.cosy.feddb.core.execution.message.ExecutionMessageRoundDTO;
import bio.cosy.feddb.core.execution.run.dto.*;
import bio.cosy.feddb.core.execution.run.types.AppRunTypeEnum;
import bio.cosy.feddb.core.execution.run.types.RunStatusTypes;
import bio.cosy.feddb.core.helper.FileHelper;
import de.unihamburg.daibetes.api.app.author.FederatedAppAuthorBO;
import de.unihamburg.daibetes.api.app.version.FederatedAppVersionAO;
import de.unihamburg.daibetes.api.app.version.FederatedAppVersionEntity;
import de.unihamburg.daibetes.api.model.ModelBO;
import de.unihamburg.daibetes.api.development.run.federated.FederatedParticipantUpdateDTO;
import de.unihamburg.daibetes.api.development.run.federated.FederatedRoundMessageCreateDTO;
import de.unihamburg.daibetes.api.development.run.federated.FederatedTestRunCreateDTO;
import de.unihamburg.daibetes.execution.attempt.ExecutionAttemptAO;
import de.unihamburg.daibetes.execution.attempt.ExecutionAttemptEntity;
import de.unihamburg.daibetes.execution.attempt.ExecutionAttemptLifecycle;
import de.unihamburg.daibetes.execution.experiment.ExecutionExperimentAO;
import de.unihamburg.daibetes.execution.experiment.ExecutionExperimentDTO;
import de.unihamburg.daibetes.execution.experiment.ExecutionExperimentEntity;
import de.unihamburg.daibetes.execution.file.ExecutionFileBO;
import de.unihamburg.daibetes.execution.run.ExecutionRunAO;
import de.unihamburg.daibetes.execution.run.ExecutionRunDetailDTO;
import de.unihamburg.daibetes.execution.run.ExecutionRunDetailMapper;
import de.unihamburg.daibetes.execution.run.ExecutionRunEntity;
import de.unihamburg.daibetes.execution.step.ExecutionRunStepEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotFoundException;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.List;
import java.util.Map;

/**
 * Test runs of an app in the developer's own app engine, as runs of the execution. A test run is a run of
 * one step, an experiment one whose step searches a grid of hyper parameters; a federated test run is a run
 * of one federated step with an attempt per participant. The app addresses a test run by its attempt and a
 * federated test run by its run; the work goes to the setup, progress and cleanup BOs.
 */
@ApplicationScoped
public class TestRunBO {

    @Inject
    FederatedAppAuthorBO authorBO;

    @Inject
    FederatedAppVersionAO appVersionAO;

    @Inject
    ExecutionRunAO runAO;

    @Inject
    ExecutionAttemptAO attemptAO;

    @Inject
    ExecutionExperimentAO experimentAO;

    @Inject
    ExecutionRunDetailMapper mapper;

    @Inject
    ModelBO modelBO;

    @Inject
    TestRunSetupBO setupBO;

    @Inject
    TestRunProgressBO progressBO;

    @Inject
    ExecutionAttemptLifecycle lifecycle;

    @Inject
    TestRunCleanupBO cleanupBO;

    @Inject
    ExecutionFileBO executionFileBO;

    @Transactional
    public List<ExecutionRunDetailDTO> listTransactional(Long appId, ExecutionMode mode, boolean experiments, String keycloakId) {
        requireAuthor(appId, keycloakId);
        return runAO.listTestRunsOfApp(appId, mode, experiments).stream()
                .map(run -> mapper.mapRunEntityToDetailDTO(run, false))
                .toList();
    }

    @Transactional
    public ExecutionRunDetailDTO findRunTransactional(Long appId, Long runId, String keycloakId) {
        requireAuthor(appId, keycloakId);
        return mapper.mapRunEntityToDetailDTO(testRunOf(appId, runId), true);
    }

    @Transactional
    public Long startTestTransactional(Long appId, TestRunCreateDTO create, String keycloakId) {
        return setupBO.startTest(appVersionOf(appId, create.getFederatedAppVersionId(), keycloakId), create, keycloakId).getId();
    }

    @Transactional
    public ExecutionRunDetailDTO startExperimentTransactional(Long appId, CreateExperimentDTO create, String keycloakId) {
        ExecutionRunEntity run = setupBO.startExperiment(appVersionOf(appId, create.getFederatedAppVersionId(), keycloakId), create, keycloakId);
        return mapper.mapRunEntityToDetailDTO(run, false);
    }

    @Transactional
    public ExecutionRunDetailDTO findExperimentTransactional(Long appId, Long experimentId, String keycloakId) {
        requireAuthor(appId, keycloakId);
        return mapper.mapRunEntityToDetailDTO(experimentOf(appId, experimentId).getRuns().getLast(), true);
    }

    @Transactional
    public ExecutionExperimentDTO updateExperimentTransactional(Long appId, Long experimentId, ExecutionExperimentDTO update, String keycloakId) {
        requireAuthor(appId, keycloakId);
        return setupBO.updateExperiment(experimentOf(appId, experimentId), update);
    }

    @Transactional
    public void keepModelTransactional(ExecutionAttemptEntity attempt, ModelSubDataDTO model) {
        FederatedAppVersionEntity version = attempt.getStep().getFederatedAppVersion();
        ExecutionRunEntity run = attempt.getStep().getRun();
        requireAuthor(version.getFederatedApp().getId(), run.getKeycloakId());
        modelBO.saveModel(version.getId(), run.getExperiment() == null ? null : run.getExperiment().getId(),
                version.getFederatedApp().getId(), model, run.getKeycloakId());
    }

    @Transactional
    public Long startFederatedTestTransactional(Long appId, FederatedTestRunCreateDTO create, String keycloakId) {
        return setupBO.startFederatedTest(appVersionOf(appId, create.getFederatedAppVersionId(), keycloakId), create, keycloakId).getId();
    }

    @Transactional
    public StartRunDTO startupOfTransactional(Long attemptId) {
        return setupBO.startupOf(testAttemptOf(attemptId));
    }

    @Transactional
    public StartRunDTO federatedStartupOfTransactional(ExecutionRunStepEntity step) {
        return setupBO.federatedStartupOf(step);
    }

    @Transactional
    public void updateRunTransactional(UpdateRunDTO updateTest, AppRunTypeEnum runType) {
        if (runType != null && runType.isFederated()) {
            FederatedRunLifecycleDTO update = new FederatedRunLifecycleDTO();
            update.setId(updateTest.getRunId());
            update.setStatus(updateTest.getStatus() != null ? updateTest.getStatus().name() : null);
            update.setError(updateTest.getError());
            updateFederatedRunTransactional(update);
        } else {
            lifecycle.updateTransactional(updateTest.getRunId(), updateTest);
        }
    }

    @Transactional
    public void finishRunTransactional(FinishRunDTO finishTest, AppRunTypeEnum runType) {
        if (runType != null && runType.isFederated()) {
            finishFederatedTransactional(finishTest);
        } else {
            lifecycle.finish(finishTest.getRunId(), finishTest);
        }
    }

    @Transactional
    public ExecutionMessageLogDTO storeLogTransactional(Long runId, ExecutionMessageLogDTO log, AppRunTypeEnum runType) {
        lifecycle.storeLogTransactional(runType.isFederated() ? participantAttemptIdOf(runId, log.getWorkerId()) : runId, log);
        return log;
    }

    @Transactional
    public ExecutionMessageMetricDTO storeMetricTransactional(Long runId, ExecutionMessageMetricDTO metric, AppRunTypeEnum runType) {
        lifecycle.storeMetricTransactional(runType.isFederated() ? participantAttemptIdOf(runId, metric.getWorkerId()) : runId, metric);
        return metric;
    }

    @Transactional
    public byte[] downloadOutputTransactional(Long appId, Long id, AppRunTypeEnum runType, String keycloakId) {
        requireAuthor(appId, keycloakId);
        ExecutionAttemptEntity attempt = runType == AppRunTypeEnum.EXPERIMENT_RUN
                ? testAttemptOf(id) : testRunOf(appId, id).getSteps().getFirst().getSelectedAttempt();
        Map<String, Object> fields = Map.of();
        Map<String, File> files = Map.of();
        if (attempt != null) {
            if (runType == AppRunTypeEnum.EXPERIMENT_RUN) {
                testRunOf(appId, attempt.getStep().getRun().getId());
            }
            fields = mapper.mapJsonToFields(attempt.getResult());
            files = executionFileBO.loadForAttempt(attempt.getId(), ExecutionFileDirection.OUTPUT);
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        FileHelper.createOutputZip(output, fields, files);
        return output.toByteArray();
    }

    @Transactional
    public List<ExecutionRunDetailDTO> runsOfTransactional(List<Long> runIds) {
        return runAO.listByIds(runIds).stream().map(run -> mapper.mapRunEntityToDetailDTO(run, false)).toList();
    }

    @Transactional
    public void updateFederatedRunTransactional(FederatedRunLifecycleDTO update) {
        ExecutionRunStepEntity step = federatedStepOf(update.getId());
        RunStatusTypes status = update.toRunStatus();
        if (status != null && RunStatusTypes.isFinalStatus(status)) {
            cleanupBO.endFederatedStep(step, status, update.getError());
        } else {
            progressBO.updateFederatedRun(step, update);
        }
    }

    @Transactional
    public void finishFederatedTransactional(FinishRunDTO finish) {
        cleanupBO.finishFederated(federatedStepOf(finish.getRunId()), finish);
    }

    @Transactional
    public void updateParticipantTransactional(FederatedParticipantUpdateDTO update) {
        Long attemptId = participantAttemptIdOf(update.getFederatedRunId(), update.getParticipantId());
        progressBO.updateParticipant(attemptAO.findByIdWrite(attemptId)
                .orElseThrow(() -> new NotFoundException("Participant attempt " + attemptId + " not found")), update);
    }

    @Transactional
    public ExecutionMessageRoundDTO storeRoundMessageTransactional(FederatedRoundMessageCreateDTO create) {
        return progressBO.storeRound(participantAttemptIdOf(create.getFederatedRunId(), create.getReportingParticipantId()), create);
    }

    @Transactional
    public void closeAllOnDisconnectTransactional(Long appId) {
        cleanupBO.closeAllOnDisconnect(appId);
    }

    @Transactional
    public void failAttemptTransactional(Long attemptId, String error) {
        cleanupBO.failAttempt(attemptId, error);
    }

    private FederatedAppVersionEntity appVersionOf(Long appId, Long appVersionId, String keycloakId) {
        return appVersionAO.findOfAppAuthorCheckedOptional(appVersionId, appId, keycloakId)
                .orElseThrow(() -> new NotFoundException("App version " + appVersionId + " not found for app " + appId));
    }

    private ExecutionRunEntity testRunOf(Long appId, Long runId) {
        return runAO.findTestRunOfAppOptional(runId, appId)
                .orElseThrow(() -> new NotFoundException("Test run " + runId + " not found"));
    }

    private ExecutionExperimentEntity experimentOf(Long appId, Long experimentId) {
        return experimentAO.findOfAppWithRunsOptional(experimentId, appId)
                .orElseThrow(() -> new NotFoundException("Experiment " + experimentId + " not found"));
    }

    private ExecutionRunStepEntity federatedStepOf(Long runId) {
        return runAO.findFederatedTestRunOptional(runId)
                .map(run -> run.getSteps().getFirst())
                .orElseThrow(() -> new NotFoundException("Federated test run " + runId + " not found"));
    }

    // Loaded with its write lock: the lifecycle reuses the managed attempt, an unlocked one would stay stale.
    private Long participantAttemptIdOf(Long runId, String participantId) {
        return attemptAO.findParticipantAttemptWrite(runId, participantId)
                .or(() -> attemptAO.findSelectedTestAttemptWrite(runId))
                .map(ExecutionAttemptEntity::getId)
                .orElseThrow(() -> new NotFoundException("Federated test run " + runId + " not found"));
    }

    private ExecutionAttemptEntity testAttemptOf(Long attemptId) {
        return attemptAO.findTestAttemptOptional(attemptId)
                .orElseThrow(() -> new NotFoundException("Test run attempt " + attemptId + " not found"));
    }

    public void requireAuthor(Long appId, String keycloakId) {
        if (!authorBO.isUserAuthor(keycloakId, appId)) {
            throw new ForbiddenException();
        }
    }

    @Transactional
    public boolean acceptsReportOfAppTransactional(Long appId, Long id, boolean federated) {
        if (id == null) {
            throw new ForbiddenException("Execution identity is required");
        }
        if (federated) {
            ExecutionRunEntity run = testRunOf(appId, id);
            return !run.hasEnded() && run.getEngineMode() == null;
        } else {
            ExecutionAttemptEntity attempt = testAttemptOf(id);
            testRunOf(appId, attempt.getStep().getRun().getId());
            // Ownership is checked even for late reports; an ended execution is simply ignored.
            return !attempt.hasEnded() && !de.unihamburg.daibetes.api.development.engine.TestEngineRunBO.isRunContainer(attempt);
        }
    }
}
