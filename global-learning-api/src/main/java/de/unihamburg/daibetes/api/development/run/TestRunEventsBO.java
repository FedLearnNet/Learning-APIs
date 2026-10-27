package de.unihamburg.daibetes.api.development.run;

import bio.cosy.feddb.core.execution.ExecutionMode;
import bio.cosy.feddb.core.execution.ExecutionPurpose;
import bio.cosy.feddb.core.execution.event.AttemptLaunchEvent;
import bio.cosy.feddb.core.execution.event.ExecutionRunsChangedEvent;
import bio.cosy.feddb.core.execution.run.dto.AppMessageWrapperDTO;
import bio.cosy.feddb.core.execution.run.types.AppConnectionTypesEnum;
import bio.cosy.feddb.core.execution.run.types.AppMessageTypeEnum;
import bio.cosy.feddb.core.execution.run.types.AppRunTypeEnum;
import de.unihamburg.daibetes.api.development.testembed.TestEmbedConnections;
import io.quarkus.virtual.threads.VirtualThreads;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.TransactionPhase;
import jakarta.inject.Inject;

import java.util.concurrent.ExecutorService;

@ApplicationScoped
public class TestRunEventsBO {

    @Inject
    TestRunLauncher launcher;

    @Inject
    TestRunBO testRunBO;

    @Inject
    TestEmbedConnections connections;

    @Inject
    @VirtualThreads
    ExecutorService virtualThreads;

    // Claiming needs a transaction of its own, which cannot begin on the committing thread.
    void onAttemptLaunch(@Observes(during = TransactionPhase.AFTER_SUCCESS) AttemptLaunchEvent event) {
        virtualThreads.execute(() -> launcher.launch(event.getAttemptId()));
    }

    // Changed test runs go to the developer's frontend over the testembed socket.
    void onRunsChanged(@Observes ExecutionRunsChangedEvent event) {
        if (event.getPurpose() != ExecutionPurpose.TEST) {
            return;
        }
        testRunBO.runsOfTransactional(event.getRunIds()).stream()
                .filter(run -> run.getFederatedAppId() != null)
                .forEach(run -> {
                    boolean federated = run.getMode() == ExecutionMode.FEDERATED;
                    AppRunTypeEnum runType = federated ? AppRunTypeEnum.FEDERATED_TEST_RUN
                            : run.getExperimentId() != null ? AppRunTypeEnum.EXPERIMENT_RUN : AppRunTypeEnum.TEST_RUN;
                    connections.send(run.getFederatedAppId(), AppConnectionTypesEnum.CLIENT, new AppMessageWrapperDTO<>(
                            federated ? AppMessageTypeEnum.UPDATE_FEDERATED_RUN : AppMessageTypeEnum.UPDATE_RUN, run, runType));
                });
    }
}
