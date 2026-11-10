package de.unihamburg.daibetes.api.development.run;

import bio.cosy.feddb.core.execution.ExecutionMode;
import bio.cosy.feddb.core.execution.run.dto.StartRunDTO;
import bio.cosy.feddb.core.security.Scope;
import bio.cosy.feddb.core.security.ToolApiKeyService;
import bio.cosy.feddb.core.execution.run.dto.AppMessageWrapperDTO;
import bio.cosy.feddb.core.execution.run.types.AppConnectionTypesEnum;
import bio.cosy.feddb.core.execution.run.types.AppMessageTypeEnum;
import bio.cosy.feddb.core.execution.run.types.AppRunTypeEnum;
import bio.cosy.feddb.core.execution.run.types.RunStatusTypes;
import de.unihamburg.daibetes.api.development.testembed.TestEmbedConnections;
import de.unihamburg.daibetes.execution.run.ExecutionRunAO;
import de.unihamburg.daibetes.execution.attempt.ExecutionAttemptAO;
import de.unihamburg.daibetes.execution.attempt.ExecutionAttemptEntity;
import de.unihamburg.daibetes.execution.step.ExecutionRunStepAO;
import de.unihamburg.daibetes.execution.step.ExecutionRunStepEntity;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.OptimisticLockException;
import jakarta.transaction.Transactional;

import java.util.List;

@ApplicationScoped
public class TestRunLauncher {

    @Inject
    ExecutionAttemptAO attemptAO;

    @Inject
    ToolApiKeyService apiKeys;

    @Inject
    ExecutionRunStepAO stepAO;

    @Inject
    TestRunBO testRunBO;

    @Inject
    TestEmbedConnections connections;

    @Inject
    ExecutionRunAO runAO;

    @Inject
    TestRunSetupBO setupBO;

    public void launchPending(Long appId) {
        listPendingAttemptIdsTransactional(appId).forEach(this::launch);
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    List<Long> listPendingAttemptIdsTransactional(Long appId) {
        return attemptAO.listPendingTestAttemptsOfApp(appId).stream()
                .map(ExecutionAttemptEntity::getId)
                .toList();
    }

    public void launch(Long attemptId) {
        Long appId = findAppIdOfTestAttemptTransactional(attemptId);
        if (appId == null || connections.of(appId, AppConnectionTypesEnum.APP).findAny().isEmpty()) {
            return;
        }
        TestRunLaunchDTO launch;
        try {
            launch = claimTransactional(attemptId);
        } catch (RuntimeException e) {
            if (isClaimedElsewhere(e)) {
                Log.debugf("Test run attempt %d was claimed by another launch", attemptId);
                return;
            }
            Log.errorf(e, "Could not start test run attempt %d", attemptId);
            testRunBO.failAttemptTransactional(attemptId, "Test run could not be started: " + e.getMessage());
            return;
        }
        if (launch == null) {
            return;
        }
        connections.send(launch.getAppId(), AppConnectionTypesEnum.APP, launch.getMessage());
        Log.infof("Test run attempt %d handed to the app engine of app %d", attemptId, launch.getAppId());
    }

    private static boolean isClaimedElsewhere(Throwable e) {
        for (Throwable cause = e; cause != null; cause = cause.getCause()) {
            if (cause instanceof OptimisticLockException) {
                return true;
            }
        }
        return false;
    }

    // The app of a test attempt; none for any other attempt.
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    Long findAppIdOfTestAttemptTransactional(Long attemptId) {
        return attemptAO.findTestAttemptOptional(attemptId)
                .map(attempt -> attempt.getStep().getFederatedAppVersion().getFederatedApp().getId())
                .orElse(null);
    }
    // Claims the attempt, for a federated test run every pending participant, so a repeated event starts nothing twice.
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    TestRunLaunchDTO claimTransactional(Long attemptId) {
        ExecutionAttemptEntity attempt = attemptAO.findTestAttemptOptional(attemptId).orElse(null);
        if (attempt == null) {
            return null;
        }
        ExecutionRunStepEntity step = attempt.getStep();
        Long appId = step.getFederatedAppVersion().getFederatedApp().getId();
        // While a test engine container runs it is the app's only engine, so it serves this run
        runAO.findConnectingEngineOfApp(appId)
                .filter(engine -> step.getRun().getTestEngine() == null)
                .ifPresent(engine -> step.getRun().setTestEngine(engine));
        if (step.getRun().getMode() == ExecutionMode.FEDERATED) {
            ExecutionRunStepEntity locked = stepAO.findByIdWrite(step.getId()).orElseThrow();
            List<ExecutionAttemptEntity> pending = locked.getAttempts().stream()
                    .filter(participant -> participant.getStatus() == RunStatusTypes.PENDING)
                    .toList();
            if (pending.isEmpty()) {
                return null;
            }
            pending.forEach(participant -> participant.begin(RunStatusTypes.INITIALIZED));
            // a test engine of the platform has only the files the platform puts into its volume
            if (step.getRun().getTestEngine() != null) {
                setupBO.stageOnEngine(step.getRun().getTestEngine(), pending);
            }
            StartRunDTO start = testRunBO.federatedStartupOfTransactional(locked);
            withCredential(start, locked.getSelectedAttempt().getId());
            return new TestRunLaunchDTO(appId,
                    new AppMessageWrapperDTO<>(AppMessageTypeEnum.START_FEDERATED_RUN, start,
                            AppRunTypeEnum.FEDERATED_TEST_RUN));
        }
        ExecutionAttemptEntity locked = attemptAO.findByIdWrite(attemptId).orElse(null);
        if (locked == null || locked.getStatus() != RunStatusTypes.PENDING) {
            return null;
        }
        locked.begin(RunStatusTypes.INITIALIZED);
        // The app engine keeps the model a trial of an experiment trained, not that of a plain test run.
        AppRunTypeEnum runType = step.getRun().getExperiment() != null ? AppRunTypeEnum.EXPERIMENT_RUN : AppRunTypeEnum.TEST_RUN;
        StartRunDTO start = testRunBO.startupOfTransactional(attemptId);
        withCredential(start, attemptId);
        return new TestRunLaunchDTO(appId,
                new AppMessageWrapperDTO<>(AppMessageTypeEnum.START_RUN, start, runType));
    }
    private void withCredential(StartRunDTO start, Long attemptId) {
        start.setExecutionId(attemptId);
        start.setExecutionApiKey(apiKeys.issue(Scope.EXECUTION_ATTEMPT, attemptId));
    }
}
