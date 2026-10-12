package bio.cosy.feddb.local.agent.etl.transform.model;

import bio.cosy.feddb.core.api.file.analytics.ColumnProfile;
import bio.cosy.feddb.local.agent.etl.transform.assistant.TransformerAssistantUser;
import bio.cosy.feddb.local.api.importer.connector.ConnectorConfigDTO;
import bio.cosy.feddb.local.api.importer.transformer.ConnectorTransformerDTO;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


// mappedColumns: source column -> what it is mapped onto, the import maps every column only once
// steps: what the agent tried for the current schema node, logged as one line when the node is done
// answers: what the user answered during the run, by question, to not ask the same twice
public record TransformerRunContext(ConnectorConfigDTO config,
                                    String patientIdColumn,
                                    String keycloakId,
                                    Map<String, ColumnProfile> fileProfiles,
                                    Map<String, String> mappedColumns,
                                    List<String> steps,
                                    TransformerAssistantUser user,
                                    Map<String, String> answers) {

    public TransformerRunContext(ConnectorConfigDTO config, String patientIdColumn, String keycloakId,
                                 Map<String, ColumnProfile> fileProfiles, Map<String, String> mappedColumns,
                                 List<String> steps, TransformerAssistantUser user) {
        this(config, patientIdColumn, keycloakId, fileProfiles, mappedColumns, steps, user, new ConcurrentHashMap<>());
    }

    public TransformerRunContext forNode() {
        return new TransformerRunContext(config, patientIdColumn, keycloakId, fileProfiles, mappedColumns,
                new ArrayList<>(), user, answers);
    }

    public void step(String format, Object... args) {
        steps.add(String.format(format, args));
    }

    public ConnectorConfigDTO configWith(List<ConnectorTransformerDTO> transformers) {
        List<ConnectorTransformerDTO> chain = new ArrayList<>(config.getTransformer());
        chain.addAll(transformers);
        return new ConnectorConfigDTO(config.getConnectorId(), config.getCohortId(), config.getInputConfig(),
                config.getFileInfo(), chain, config.getSchemaMapping(), config.getMergeConfig(), config.getPivotConfig());
    }
}
