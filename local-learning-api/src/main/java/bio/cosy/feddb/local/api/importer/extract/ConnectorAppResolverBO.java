package bio.cosy.feddb.local.api.importer.extract;

import bio.cosy.feddb.core.api.app.FederatedAppDetailDTO;
import bio.cosy.feddb.local.api.importer.connector.input.AppBasedUploadSettingsDTO;
import bio.cosy.feddb.local.api.importer.transformer.ConnectorTransformerDTO;
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
        FederatedAppDetailDTO app = resolveRunnableApp(settings.getAppSlug());
        settings.setAppVersionId(Math.toIntExact(app.getLatestVersionId()));
        settings.setAppImage(app.getImageName());
    }

    public void resolve(ConnectorTransformerDTO transformer) {
        if (transformer == null || transformer.getAppSlug() == null || transformer.getAppSlug().isBlank()) {
            return;
        }
        FederatedAppDetailDTO app = resolveRunnableApp(transformer.getAppSlug());
        transformer.setAppVersionId(app.getLatestVersionId());
        transformer.setAppImage(app.getImageName());
    }

    private FederatedAppDetailDTO resolveRunnableApp(String slug) {
        FederatedAppDetailDTO app = store.getApp(slug);
        if (app == null || app.getLatestVersionId() == null
                || app.getImageName() == null || app.getImageName().isBlank()) {
            throw new IllegalStateException("Connector app '" + slug
                    + "' has no runnable version yet. Wait for its platform import/build to finish and retry the connector run.");
        }
        return app;
    }
}
