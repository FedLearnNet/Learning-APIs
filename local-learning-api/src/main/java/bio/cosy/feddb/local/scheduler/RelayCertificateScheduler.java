package bio.cosy.feddb.local.scheduler;

import bio.cosy.feddb.local.api.learning.project.run.FederatedLearningRunBO;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class RelayCertificateScheduler {

    @Inject
    FederatedLearningRunBO federatedLearningRunBO;

    @Scheduled(every = "30s", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void retryPendingCertificates() {
        federatedLearningRunBO.retryPendingTransactional();
    }
}
