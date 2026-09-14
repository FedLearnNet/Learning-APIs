package bio.cosy.feddb.local;

import bio.cosy.feddb.local.api.cohort.AutoSubscriberBO;
import bio.cosy.feddb.local.api.importer.connector.ConnectorScheduleBO;
import bio.cosy.feddb.local.config.FLNetClientConfig;
import io.quarkus.logging.Log;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

@ApplicationScoped
public class Startup {

    @Inject
    FLNetClientConfig config;

    @Inject
    AutoSubscriberBO autoSubscriberBO;

    @Inject
    ConnectorScheduleBO connectorScheduleBO;

    public void onStartup(@Observes StartupEvent event) {
        try {
            Log.infof("Starting Quarkus");

            if (Boolean.TRUE.equals(config.autoSubscribe().enabled())) {
                Log.info("AutoSubscribe is enabled, subscribing to FLNet groups in the background");
                // Fire-and-forget: each group runs on its own worker thread, so startup is not blocked.
                autoSubscriberBO.subscribeToFLNet(config.autoSubscribe().groups())
                        .subscribe().with(
                                success -> Log.infof("AutoSubscribe finished, allSucceeded=%s", success),
                                failure -> Log.errorf("AutoSubscribe failed: %s", failure.getMessage(), failure)
                        );
            } else {
                Log.info("AutoSubscribe is disabled, skipping FLNet subscription");
            }

            connectorScheduleBO.initTransactional();

        } catch (Exception e) {
            Log.errorf("\n====================\nError during startup:\n%s\n====================", e.getMessage(), e);

        }
    }

}
