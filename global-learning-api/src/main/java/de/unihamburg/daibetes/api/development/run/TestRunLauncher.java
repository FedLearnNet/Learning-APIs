package de.unihamburg.daibetes.api.development.run;

import bio.cosy.feddb.core.execution.ExecutionMode;
import bio.cosy.feddb.core.execution.run.dto.AppMessageWrapperDTO;
import bio.cosy.feddb.core.execution.run.types.AppConnectionTypesEnum;
import bio.cosy.feddb.core.execution.run.types.AppMessageTypeEnum;
import bio.cosy.feddb.core.execution.run.types.AppRunTypeEnum;
import bio.cosy.feddb.core.execution.run.types.RunStatusTypes;
import de.unihamburg.daibetes.api.development.testembed.TestEmbedConnections;
import de.unihamburg.daibetes.execution.attempt.ExecutionAttemptAO;
import de.unihamburg.daibetes.execution.attempt.ExecutionAttemptEntity;
import de.unihamburg.daibetes.execution.step.ExecutionRunStepAO;
import de.unihamburg.daibetes.execution.step.ExecutionRunStepEntity;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.util.List;

@ApplicationScoped
public class TestRunLauncher {

    @Inject
    ExecutionAttemptAO attemptAO;

    @Inject
    ExecutionRunStepAO stepAO;

    @Inject
    TestRunBO testRunBO;

    @Inject
    TestEmbedConnections connections;

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
        if (step.getRun().getMode() == ExecutionMode.FEDERATED) {
            ExecutionRunStepEntity locked = stepAO.findByIdWrite(step.getId()).orElseThrow();
            List<ExecutionAttemptEntity> pending = locked.getAttempts().stream()
                    .filter(participant -> participant.getStatus() == RunStatusTypes.PENDING)
                    .toList();
            if (pending.isEmpty()) {
                return null;
            }
            pending.forEach(participant -> participant.begin(RunStatusTypes.INITIALIZED));
            return new TestRunLaunchDTO(appId,
                    new AppMessageWrapperDTO<>(AppMessageTypeEnum.START_FEDERATED_RUN, testRunBO.federatedStartupOfTransactional(locked),
                            AppRunTypeEnum.FEDERATED_TEST_RUN));
        }
        ExecutionAttemptEntity locked = attemptAO.findByIdWrite(attemptId).orElse(null);
        if (locked == null || locked.getStatus() != RunStatusTypes.PENDING) {
            return null;
        }
        locked.begin(RunStatusTypes.INITIALIZED);
        // The app engine keeps the model a trial of an experiment trained, not that of a plain test run.
        AppRunTypeEnum runType = step.getRun().getExperiment() != null ? AppRunTypeEnum.EXPERIMENT_RUN : AppRunTypeEnum.TEST_RUN;
        return new TestRunLaunchDTO(appId,
                new AppMessageWrapperDTO<>(AppMessageTypeEnum.START_RUN, testRunBO.startupOfTransactional(attemptId), runType));
    }
}
