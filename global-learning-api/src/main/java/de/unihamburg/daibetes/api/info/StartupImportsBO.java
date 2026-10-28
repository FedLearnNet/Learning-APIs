package de.unihamburg.daibetes.api.info;

import bio.cosy.feddb.core.api.info.BaseStartupImportsBO;
import bio.cosy.feddb.core.api.info.StartupImportState;
import de.unihamburg.daibetes.services.DataModelerInfoService;
import io.quarkus.cache.CacheResult;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.rest.client.inject.RestClient;

@ApplicationScoped
public class StartupImportsBO extends BaseStartupImportsBO {

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
            return dataModelerInfoService.getInfo().getStartupImports();
        } catch (RuntimeException e) {
            // not reachable yet, e.g. still starting
            Log.debugf("Data modeler info not available: %s", e.getMessage());
            return StartupImportState.IMPORTING;
        }
    }
}
