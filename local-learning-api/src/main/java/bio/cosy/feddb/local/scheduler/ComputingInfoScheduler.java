package bio.cosy.feddb.local.scheduler;

import bio.cosy.feddb.local.api.eam.ClientManager;
import bio.cosy.feddb.local.services.orch.ComputingInfoBO;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class ComputingInfoScheduler {

    @Inject
    ComputingInfoBO computingInfo;
    
    @Inject
    ClientManager clientManager;

    @Scheduled(every = "5m", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void refreshSystemInfo() {
        computingInfo.refresh();
        clientManager.sendSystemInfo();
    }
}
