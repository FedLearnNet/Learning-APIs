package bio.cosy.feddb.local.execution.queue;

import bio.cosy.feddb.core.execution.event.RunFinishedEvent;
import io.quarkus.virtual.threads.VirtualThreads;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.TransactionPhase;
import jakarta.inject.Inject;

import java.util.concurrent.ExecutorService;

@ApplicationScoped
public class ExecutionQueueEventsBO {

    @Inject
    ExecutionQueueBO queueBO;

    @Inject
    @VirtualThreads
    ExecutorService virtualThreads;

    void onRunFinished(@Observes(during = TransactionPhase.AFTER_SUCCESS) RunFinishedEvent event) {
        virtualThreads.execute(queueBO::launchNextTransactional);
    }
}
