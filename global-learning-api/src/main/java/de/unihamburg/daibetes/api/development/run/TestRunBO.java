package de.unihamburg.daibetes.api.development.run;

import bio.cosy.feddb.core.execution.ExecutionMode;
import bio.cosy.feddb.core.execution.message.ExecutionMessageLogDTO;
import bio.cosy.feddb.core.execution.message.ExecutionMessageMetricDTO;
import bio.cosy.feddb.core.execution.message.ExecutionMessageRoundDTO;
import bio.cosy.feddb.core.execution.run.dto.*;
import bio.cosy.feddb.core.execution.run.types.AppRunTypeEnum;
import bio.cosy.feddb.core.execution.run.types.RunStatusTypes;
import de.unihamburg.daibetes.api.app.author.FederatedAppAuthorBO;
import de.unihamburg.daibetes.api.app.version.FederatedAppVersionAO;
import de.unihamburg.daibetes.api.app.version.FederatedAppVersionEntity;
import de.unihamburg.daibetes.api.development.run.federated.FederatedParticipantUpdateDTO;
import de.unihamburg.daibetes.api.development.run.federated.FederatedRoundMessageCreateDTO;
import de.unihamburg.daibetes.api.development.run.federated.FederatedTestRunCreateDTO;
import de.unihamburg.daibetes.execution.attempt.ExecutionAttemptAO;
import de.unihamburg.daibetes.execution.attempt.ExecutionAttemptEntity;
import de.unihamburg.daibetes.execution.experiment.ExecutionExperimentAO;
import de.unihamburg.daibetes.execution.experiment.ExecutionExperimentDTO;
import de.unihamburg.daibetes.execution.experiment.ExecutionExperimentEntity;
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

import java.util.LinkedHashMap;
import java.util.List;

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
    TestRunSetupBO setupBO;

    @Inject
    TestRunProgressBO progressBO;

    @Inject
    TestRunCleanupBO cleanupBO;

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
    public ExecutionRunDetailDTO runOfAttemptTransactional(Long attemptId, String keycloakId) {
        ExecutionRunDetailDTO run = mapper.mapRunEntityToDetailDTO(testAttemptOf(attemptId).getStep().getRun(), false);
        requireAuthor(run.getFederatedAppId(), keycloakId);
        return run;
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

    public void updateAttempt(UpdateRunDTO update) {
        progressBO.updateAttempt(update);
    }

    public void finishAttempt(FinishRunDTO finish) {
        cleanupBO.finishAttempt(finish);
    }

    @Transactional
    public ExecutionMessageLogDTO storeLogTransactional(Long runId, ExecutionMessageLogDTO log, AppRunTypeEnum runType) {
        progressBO.storeLog(runType.isFederated() ? participantAttemptOf(runId, log.getWorkerId()).getId() : runId, log);
        return log;
    }

    @Transactional
    public ExecutionMessageMetricDTO storeMetricTransactional(Long runId, ExecutionMessageMetricDTO metric, AppRunTypeEnum runType) {
        progressBO.storeMetric(runType.isFederated() ? participantAttemptOf(runId, metric.getWorkerId()).getId() : runId, metric);
        return metric;
    }

    @Transactional
    public ExecutionRunDetailDTO uploadOutputTransactional(OutputRunDataDTO output, AppRunTypeEnum runType) {
        ExecutionAttemptEntity attempt = runType.isFederated()
                ? federatedStepOf(output.getRunId()).getSelectedAttempt()
                : testAttemptOf(output.getRunId());
        progressBO.mergeOutput(attempt, output);
        return mapper.mapRunEntityToDetailDTO(attempt.getStep().getRun(), false);
    }

    @Transactional
    public List<ExecutionRunDetailDTO> runsOfTransactional(List<Long> runIds) {
        return runAO.listByIds(runIds).stream().map(run -> mapper.mapRunEntityToDetailDTO(run, false)).toList();
    }

    @Transactional
    public LinkedHashMap<String, Object> outputOfTransactional(Long appId, Long runId, String keycloakId) {
        requireAuthor(appId, keycloakId);
        ExecutionAttemptEntity attempt = testRunOf(appId, runId).getSteps().getFirst().getSelectedAttempt();
        return attempt == null ? new LinkedHashMap<>() : mapper.mapJsonToFields(attempt.getResult());
    }

    @Transactional
    public LinkedHashMap<String, Object> outputOfAttemptTransactional(Long appId, Long attemptId, String keycloakId) {
        requireAuthor(appId, keycloakId);
        ExecutionAttemptEntity attempt = testAttemptOf(attemptId);
        testRunOf(appId, attempt.getStep().getRun().getId());
        return mapper.mapJsonToFields(attempt.getResult());
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
        progressBO.updateParticipant(participantAttemptOf(update.getFederatedRunId(), update.getParticipantId()), update);
    }

    @Transactional
    public ExecutionMessageRoundDTO storeRoundMessageTransactional(FederatedRoundMessageCreateDTO create) {
        return progressBO.storeRound(participantAttemptOf(create.getFederatedRunId(), create.getReportingParticipantId()), create);
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

    private ExecutionAttemptEntity participantAttemptOf(Long runId, String participantId) {
        return attemptAO.findParticipantAttemptOptional(runId, participantId)
                .orElseGet(() -> federatedStepOf(runId).getSelectedAttempt());
    }

    private ExecutionAttemptEntity testAttemptOf(Long attemptId) {
        return attemptAO.findTestAttemptOptional(attemptId)
                .orElseThrow(() -> new NotFoundException("Test run attempt " + attemptId + " not found"));
    }

    private void requireAuthor(Long appId, String keycloakId) {
        if (!authorBO.isUserAuthor(keycloakId, appId)) {
            throw new ForbiddenException();
        }
    }
}
