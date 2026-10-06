package bio.cosy.feddb.local.agent.etl.transform.model;

import bio.cosy.feddb.core.api.file.analytics.ColumnProfile;
import bio.cosy.feddb.local.agent.etl.transform.assistant.TransformerAssistantUser;
import bio.cosy.feddb.local.api.importer.connector.ConnectorConfigDTO;
import bio.cosy.feddb.local.api.importer.transformer.ConnectorTransformerDTO;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;


public record TransformerRunContext(ConnectorConfigDTO config,
                                    String patientIdColumn,
                                    String keycloakId,
                                    Map<String, ColumnProfile> fileProfiles,
                                    TransformerAssistantUser user) {

    public ConnectorConfigDTO configWith(List<ConnectorTransformerDTO> transformers) {
        List<ConnectorTransformerDTO> chain = new ArrayList<>(config.getTransformer());
        chain.addAll(transformers);
        return new ConnectorConfigDTO(config.getConnectorId(), config.getCohortId(), config.getInputConfig(),
                config.getFileInfo(), chain, config.getSchemaMapping(), config.getMergeConfig(), config.getPivotConfig());
    }
}
