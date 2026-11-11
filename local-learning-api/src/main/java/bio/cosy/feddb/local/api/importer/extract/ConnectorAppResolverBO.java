package bio.cosy.feddb.local.api.importer.extract;

import bio.cosy.feddb.core.api.app.FederatedAppDetailDTO;
import bio.cosy.feddb.local.api.importer.connector.input.AppBasedUploadSettingsDTO;
import bio.cosy.feddb.local.services.GlobalAPIStoreService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.rest.client.inject.RestClient;

@ApplicationScoped
public class ConnectorAppResolverBO {

    @Inject
    @RestClient
    GlobalAPIStoreService store;

    public void resolve(AppBasedUploadSettingsDTO settings) {
        if (settings == null || settings.getAppSlug() == null || settings.getAppSlug().isBlank()) {
            return;
        }
        FederatedAppDetailDTO app = store.getApp(settings.getAppSlug());
        if (app == null || app.getLatestVersionId() == null
                || app.getImageName() == null || app.getImageName().isBlank()) {
            throw new IllegalStateException("Extractor app '" + settings.getAppSlug()
                    + "' has no runnable version yet. Wait for its platform import/build to finish and retry the connector run.");
        }
        settings.setAppVersionId(Math.toIntExact(app.getLatestVersionId()));
        settings.setAppImage(app.getImageName());
    }
}
