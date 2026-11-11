package de.unihamburg.daibetes.api.project.training.federated;

import bio.cosy.feddb.core.execution.ExecutionPurpose;
import bio.cosy.feddb.core.execution.event.AttemptFinishedEvent;
import io.quarkus.virtual.threads.VirtualThreads;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.TransactionPhase;
import jakarta.inject.Inject;

import java.util.concurrent.ExecutorService;

@ApplicationScoped
public class FederatedTrainingEventsBO {

    @Inject
    FederatedTrainingBO trainingBO;

    @Inject
    @VirtualThreads
    ExecutorService virtualThreads;

    // An ended aggregator of the platform moves its step on or fails it; that needs a transaction of its own,
    // which cannot begin on the committing thread.
    void onAttemptFinished(@Observes(during = TransactionPhase.AFTER_SUCCESS) AttemptFinishedEvent event) {
        if (event.getPurpose() == ExecutionPurpose.TRAINING) {
            virtualThreads.execute(() -> trainingBO.aggregatorEndedTransactional(event.getAttemptId()));
        }
    }
}
