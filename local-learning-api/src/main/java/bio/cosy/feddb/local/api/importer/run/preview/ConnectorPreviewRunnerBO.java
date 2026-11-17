package bio.cosy.feddb.local.api.importer.run.preview;

import bio.cosy.feddb.local.api.importer.connector.ConnectorConfigDTO;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;
import java.util.Map;

/**
 * Runs a whole transformer chain on the preview sample without waiting for the wizard: unlike the
 * connector preview, app-based transformers are executed right away, each in a container started for it.
 * Stages of a saved connector are served from and written to the preview cache.
 */
@ApplicationScoped
public class ConnectorPreviewRunnerBO {

    @Inject
    ConnectorPreviewBO previewBO;

    @Inject
    AppTransformerPreviewBO appTransformerPreviewBO;

    public List<Map<String, Object>> run(ConnectorConfigDTO config, String keycloakId) {
        return previewBO.run(config, (transformer, rows) ->
                appTransformerPreviewBO.transformAppBased(transformer, rows, keycloakId, step -> {
                })).getRows();
    }
}
