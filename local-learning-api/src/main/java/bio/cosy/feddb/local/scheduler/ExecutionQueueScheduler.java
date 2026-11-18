package bio.cosy.feddb.local.scheduler;

import bio.cosy.feddb.local.execution.queue.ExecutionQueueBO;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class ExecutionQueueScheduler {

    @Inject
    ExecutionQueueBO queueBO;

    @Scheduled(every = "30s", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void launchNext() {
        queueBO.launchNextTransactional();
    }
}
