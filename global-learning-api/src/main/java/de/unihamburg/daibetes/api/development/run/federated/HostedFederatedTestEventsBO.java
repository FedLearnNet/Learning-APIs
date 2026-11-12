package de.unihamburg.daibetes.api.development.run.federated;

import bio.cosy.feddb.core.execution.event.RunFinishedEvent;
import bio.cosy.feddb.core.execution.event.StepChangedEvent;
import io.quarkus.virtual.threads.VirtualThreads;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.TransactionPhase;
import jakarta.inject.Inject;

import java.util.concurrent.ExecutorService;

@ApplicationScoped
public class HostedFederatedTestEventsBO {

    @Inject
    HostedFederatedTestBO hostedTestBO;

    @Inject
    @VirtualThreads
    ExecutorService virtualThreads;

    void onStepChanged(@Observes(during = TransactionPhase.AFTER_SUCCESS) StepChangedEvent event) {
        virtualThreads.execute(() -> hostedTestBO.settleStepTransactional(event.getStepId()));
    }

    void onRunFinished(@Observes(during = TransactionPhase.AFTER_SUCCESS) RunFinishedEvent event) {
        virtualThreads.execute(() -> hostedTestBO.cleanupRunTransactional(event.getRunId()));
    }
}
