package bio.cosy.feddb.local.scheduler;

import bio.cosy.feddb.local.api.learning.project.run.federated.FederatedLearningProgressBO;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.time.Instant;

@ApplicationScoped
public class RelayCertificateScheduler {

    @Inject
    FederatedLearningProgressBO progressBO;

    @Scheduled(every = "30s", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    @Transactional
    void retryPendingCertificates() {
        progressBO.retryPending(Instant.now());
    }
}
