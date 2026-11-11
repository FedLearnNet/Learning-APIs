package de.unihamburg.daibetes.api.info;

import bio.cosy.feddb.core.api.info.BaseStartupImportsBO;
import bio.cosy.feddb.core.api.info.StartupImportState;
import de.unihamburg.daibetes.services.DataModelerInfoService;
import io.quarkus.cache.CacheResult;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.util.concurrent.atomic.AtomicBoolean;

@ApplicationScoped
public class StartupImportsBO extends BaseStartupImportsBO {

    private final AtomicBoolean dataModelerUnavailable = new AtomicBoolean();

    @Inject
    @RestClient
    DataModelerInfoService dataModelerInfoService;

    @Override
    public StartupImportState loadState() {
        return getState().combine(loadDataModelerState());
    }

    @CacheResult(cacheName = "data-modeler-info")
    StartupImportState loadDataModelerState() {
        try {
            StartupImportState state = dataModelerInfoService.getInfo().getStartupImports();
            if (dataModelerUnavailable.getAndSet(false)) {
                Log.info("Datamodeler is reachable again; refreshing startup import readiness");
            }
            return state;
        } catch (RuntimeException e) {
            // not reachable yet, e.g. still starting
            if (dataModelerUnavailable.compareAndSet(false, true)) {
                Log.warnf("Datamodeler /info is unreachable; startup imports remain IMPORTING and client AutoSubscribe waits. "
                        + "Start the datamodeler and its database, and check quarkus.rest-client.data-modeler-service.url. Cause: %s", e.getMessage());
            }
            return StartupImportState.IMPORTING;
        }
    }
}
