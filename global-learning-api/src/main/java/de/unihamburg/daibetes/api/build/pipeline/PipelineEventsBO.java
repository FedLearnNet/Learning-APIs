package de.unihamburg.daibetes.api.build.pipeline;

import de.unihamburg.daibetes.api.app.FederatedAppBO;
import io.quarkus.logging.Log;
import io.quarkus.virtual.threads.VirtualThreads;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.TransactionPhase;
import jakarta.inject.Inject;

import java.util.concurrent.ExecutorService;

@ApplicationScoped
public class PipelineEventsBO {

    @Inject
    FederatedAppBO appBO;

    @Inject
    @VirtualThreads
    ExecutorService virtualThreads;

    // publishes once the image of the succeeded build is committed to the version
    void onBuildSucceeded(@Observes(during = TransactionPhase.AFTER_SUCCESS) PipelinePublishEvent event) {
        virtualThreads.execute(() -> {
            try {
                appBO.publishBuiltVersionTransactional(event.appVersionId(), event.publishRequest());
            } catch (RuntimeException e) {
                Log.errorf(e, "Could not publish version %d built by pipeline %d", event.appVersionId(), event.pipelineId());
            }
        });
    }
}
