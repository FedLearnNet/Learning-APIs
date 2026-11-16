package bio.cosy.feddb.local.api.info;

import bio.cosy.feddb.core.api.info.StartupImportState;
import bio.cosy.feddb.local.config.FLNetClientConfig;
import bio.cosy.feddb.local.services.GlobalAPIInfoService;
import io.quarkus.logging.Log;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.time.Duration;

@ApplicationScoped
public class PlatformStartupBO {

    @Inject
    @RestClient
    GlobalAPIInfoService globalAPIInfoService;

    @Inject
    FLNetClientConfig config;

    // emits the last seen state, IMPORTING after the timeout
    public Uni<StartupImportState> awaitStartupImports() {
        return Uni.createFrom().item(this::waitForStartupImports)
                .runSubscriptionOn(Infrastructure.getDefaultExecutor());
    }

    StartupImportState waitForStartupImports() {
        Duration timeout = config.autoSubscribe().platformWaitTimeout();
        Duration interval = config.autoSubscribe().platformPollInterval();
        long deadline = System.nanoTime() + timeout.toNanos();

        StartupImportState state = loadState();
        if (state == StartupImportState.IMPORTING) {
            Log.infof("Waiting up to %s for the Platform startup imports", timeout);
        }
        while (state == StartupImportState.IMPORTING && System.nanoTime() < deadline) {
            try {
                Thread.sleep(interval);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return state;
            }
            state = loadState();
        }
        return state;
    }

    // not cached, unlike InfoBO
    private StartupImportState loadState() {
        try {
            StartupImportState state = globalAPIInfoService.getInfo().getStartupImports();
            // older Platform without the state
            return state == null ? StartupImportState.READY : state;
        } catch (RuntimeException e) {
            Log.debugf("Platform not reachable: %s", e.getMessage());
            return StartupImportState.IMPORTING;
        }
    }
}
