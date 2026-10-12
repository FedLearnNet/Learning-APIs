package bio.cosy.feddb.local.agent.etl.transform.organizer;

import bio.cosy.feddb.core.api.datamodler.schema.SchemaNodeType;
import bio.cosy.feddb.core.api.file.analytics.ColumnProfile;
import bio.cosy.feddb.local.agent.etl.transform.assistant.TransformerAssistantMessageDTO;
import bio.cosy.feddb.local.agent.etl.transform.assistant.TransformerAssistantUser;
import bio.cosy.feddb.local.agent.etl.transform.helper.EtlAgentLog;
import bio.cosy.feddb.local.agent.etl.transform.helper.AgentDataPrivacy;
import bio.cosy.feddb.local.agent.etl.transform.model.ColumnCandidate;
import bio.cosy.feddb.local.agent.etl.transform.model.ConnectorSelectedTransformer;
import bio.cosy.feddb.local.agent.etl.transform.model.TransformerRunContext;
import bio.cosy.feddb.local.agent.etl.transform.tools.TransformerListTool;
import bio.cosy.feddb.local.api.importer.connector.ConnectorConfigDTO;
import bio.cosy.feddb.local.api.importer.connector.ConnectorDTO;
import bio.cosy.feddb.local.api.importer.extract.ConnectorExtractBO;
import bio.cosy.feddb.local.api.importer.files.table.TableData;
import bio.cosy.feddb.local.api.importer.files.table.TableSample;
import bio.cosy.feddb.local.api.importer.mapping.ConnectorMappingDTO;
import bio.cosy.feddb.local.api.importer.mapping.MappingBO;
import bio.cosy.feddb.local.api.importer.statistics.TransformedStatisticsBO;
import bio.cosy.feddb.local.api.importer.transformer.ConnectorTransformationBO;
import bio.cosy.feddb.local.api.importer.transformer.ConnectorTransformerDTO;
import bio.cosy.feddb.local.api.schema.schemanode.LocalSchemaNodeWithPathDTO;
import bio.cosy.feddb.local.api.schema.schemanode.SchemaNodeBO;
import bio.cosy.feddb.local.config.FLNetClientConfig;
import io.quarkus.logging.Log;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static bio.cosy.feddb.local.agent.etl.transform.assistant.TransformerAssistantMessageType.*;

@ApplicationScoped
public class TransformerLoop {

    static final int SAMPLE_ROWS = 200;
    static final String PATIENT_ID_QUESTION = "patientId";

    @Inject
    TransformerOrganizer transformerOrganizer;

    @Inject
    ColumnMatcher columnMatcher;

    @Inject
    AgentDataPrivacy dataPrivacy;

    @Inject
    TransformerListTool transformerListTool;

    @Inject
    TransformedStatisticsBO statisticsBO;

    @Inject
    ConnectorExtractBO extractBO;

    @Inject
    ConnectorTransformationBO transformationBO;

    @Inject
    SchemaNodeBO schemaNodeBO;

    @Inject
    MappingBO mappingBO;

    @Inject
    FLNetClientConfig config;

    public void run(ConnectorDTO connector,
                    String keycloakId,
                    Consumer<TransformerAssistantMessageDTO> events,
                    Supplier<String> instructions,
                    TransformerAssistantUser user) {
        run(connector, getSchemaNodesTransactional(connector), fileProfiles(connector), keycloakId, events, instructions,
                user);
    }

    public void run(ConnectorDTO connector,
                    List<LocalSchemaNodeWithPathDTO> schemaNodes,
                    Map<String, ColumnProfile> fileProfiles,
                    String keycloakId,
                    Consumer<TransformerAssistantMessageDTO> events,
                    Supplier<String> instructions,
                    TransformerAssistantUser user) {
        transformerListTool.resetStoreTools();
        TableSample preview = dataPrivacy.sample(loadPreview(connector), fileProfiles);
        String patientIdColumn = patientIdColumn(connector, preview, user, events);
        setPatientIdMapping(connector, patientIdColumn);
        ConnectorConfigDTO connectorConfig = config(connector, patientIdColumn);
        TransformerRunContext context = new TransformerRunContext(connectorConfig, patientIdColumn, keycloakId,
                dataPrivacy.profiles(fileProfiles), mappedColumns(connectorConfig), new ArrayList<>(), user);
        Map<Long, List<ColumnCandidate>> candidates = columnMatcher.match(preview, schemaNodes, context.fileProfiles());
        EtlAgentLog.step("start", "Map %d schema nodes from %d columns, patient id column '%s'", schemaNodes.size(),
                preview.getColumns().size(), patientIdColumn);

        schemaNodes.forEach(node ->
                events.accept(TransformerAssistantMessageDTO.node(NODE_PENDING, node.getId(), node.getPath(), null)));
        for (LocalSchemaNodeWithPathDTO node : schemaNodes) {
            preview = runForOneSchemaNode(node, preview, context, candidates, instructions, events);
        }
        EtlAgentLog.step("done", "Went through all %d schema nodes", schemaNodes.size());
    }

    public TableSample runForOneSchemaNode(LocalSchemaNodeWithPathDTO node,
                                           TableSample preview,
                                           TransformerRunContext context,
                                           Map<Long, List<ColumnCandidate>> candidates,
                                           Supplier<String> instructions,
                                           Consumer<TransformerAssistantMessageDTO> events
    ) {
        return apply(select(node, preview, context, candidates, instructions, events), preview, context, node);
    }

    private ConnectorSelectedTransformer select(LocalSchemaNodeWithPathDTO node,
                                                TableSample preview,
                                                TransformerRunContext context,
                                                Map<Long, List<ColumnCandidate>> candidates,
                                                Supplier<String> instructions,
                                                Consumer<TransformerAssistantMessageDTO> events) {
        events.accept(TransformerAssistantMessageDTO.node(NODE_STARTED, node.getId(), node.getPath(), null));
        context.steps().clear();

        ConnectorSelectedTransformer selected = transformerOrganizer.selectTransformer(context, preview, node,
                instructions.get(), candidates.getOrDefault(node.getId(), List.of()), node.getDataType());
        TransformerAssistantMessageDTO result = TransformerAssistantMessageDTO.node(NODE_RESULT, node.getId(), node.getPath(), selected);
        if (selected.isFoundTransformer()) {
            result.setVisitMapping(transformerOrganizer.selectVisit(context, preview, node, instructions.get(), selected));
        }
        events.accept(result);
        EtlAgentLog.step("node", "%s -> %s%s | %s", node.getPath(), EtlAgentLog.describe(selected),
                EtlAgentLog.describe(result.getVisitMapping()), String.join(" ; ", context.steps()));
        return selected;
    }

    private TableSample apply(ConnectorSelectedTransformer selected, TableSample preview, TransformerRunContext context,
                              LocalSchemaNodeWithPathDTO node) {
        if (selected == null || !selected.isFoundTransformer()) {
            return preview;
        }
        context.mappedColumns().put(selected.getMappedColumn(), node.getPath());
        context.config().getTransformer().addAll(selected.getSelectedTransformers());
        for (ConnectorTransformerDTO transformer : selected.getSelectedTransformers()) {
            preview = applyTransformer(preview, transformer, context.patientIdColumn());
        }
        return preview;
    }


    private String patientIdColumn(ConnectorDTO connector, TableSample preview, TransformerAssistantUser user,
                                   Consumer<TransformerAssistantMessageDTO> events) {
        String patientIdColumn = mappingBO.resolveExternalIdSourceColumn(connector.getSchemaMapping());
        if (patientIdColumn == null) {
            List<String> selected = user.selectColumns(PATIENT_ID_QUESTION,
                    "Which column holds the patient ID? It keeps the rows of one patient together.",
                    preview.getColumns(), false);
            patientIdColumn = selected.isEmpty() ? null : selected.getFirst();
        }
        EtlAgentLog.step(PATIENT_ID_QUESTION, "The patient id is in column '%s'", patientIdColumn);
        if (patientIdColumn != null) {
            events.accept(TransformerAssistantMessageDTO.patientId(config.connector().externalIdColumn(), patientIdColumn));
        }
        return patientIdColumn;
    }

    private void setPatientIdMapping(ConnectorDTO connector, String patientId) {
        List<ConnectorMappingDTO> mappings = connector.getSchemaMapping() == null ? new ArrayList<>() : new ArrayList<>(connector.getSchemaMapping());
        mappings = new ArrayList<>(ConnectorMappingDTO.removePatientIdMapping(mappings, config.connector().externalIdColumn()));
        mappings.add(ConnectorMappingDTO.getPatientIdMapping(patientId, config.connector().externalIdColumn()));
        connector.setSchemaMapping(mappings);
    }


    private ConnectorConfigDTO config(ConnectorDTO connector, String patientIdColumn) {
        List<ConnectorMappingDTO> mappings = new ArrayList<>(
                connector.getSchemaMapping() == null ? List.of() : connector.getSchemaMapping());
        if (patientIdColumn != null && mappingBO.resolveExternalIdSourceColumn(mappings) == null) {
            ConnectorMappingDTO mapping = new ConnectorMappingDTO();
            mapping.setColumn(patientIdColumn);
            mapping.setMapping(config.connector().externalIdColumn());
            mappings.add(mapping);
        }
        List<ConnectorTransformerDTO> transformers = new ArrayList<>(
                connector.getTransformer() == null ? List.of() : connector.getTransformer());
        return new ConnectorConfigDTO(connector.getId(), connector.getCohortId(), connector.getInputConfig(),
                connector.getUploadInfo(), transformers, mappings, connector.getMergeConfig(), connector.getPivotConfig());
    }

    private static Map<String, String> mappedColumns(ConnectorConfigDTO config) {
        Map<String, String> mappedColumns = new HashMap<>();
        config.getSchemaMapping().stream()
                .filter(mapping -> mapping.getColumn() != null && mapping.getMapping() != null)
                .forEach(mapping -> mappedColumns.put(mapping.getColumn(), mapping.getMapping()));
        return mappedColumns;
    }

    @Transactional
    public List<LocalSchemaNodeWithPathDTO> getSchemaNodesTransactional(ConnectorDTO connector) {
        List<ConnectorMappingDTO> mappings = connector.getSchemaMapping();
        Set<Long> mapped = mappings.stream()
                .map(ConnectorMappingDTO::getSchemaId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        return schemaNodeBO.getSchemaNodesWithPathForCohort(connector.getCohortId()).stream()
                .filter(node -> !mapped.contains(node.getId()))
                .filter(node -> node.getType().equals(SchemaNodeType.ATOMIC_ATTRIBUTE) || node.getType().equals(SchemaNodeType.LIST_ATTRIBUTE))
                .collect(Collectors.toList());
    }

    // Not wrapped in a transaction: getFileStatistics starts its own, with a longer timeout for merged files
    private Map<String, ColumnProfile> fileProfiles(ConnectorDTO connector) {
        try {
            return statisticsBO.getFileStatistics(connector.getCohortId(), connector.getInputConfig(),
                            connector.getUploadInfo(), connector.getMergeConfig(), connector.getPivotConfig())
                    .stream()
                    .filter(Objects::nonNull)
                    .collect(Collectors.toMap(ColumnProfile::name, profile -> profile, (a, b) -> a));
        } catch (RuntimeException e) {
            Log.warnf("No file statistics for the transformer selection, validating the sample only: %s", e.getMessage());
            return Map.of();
        }
    }

    private TableSample loadPreview(ConnectorDTO connector) {
        return QuarkusTransaction.joiningExisting().call(() -> {
            try (TableData data = extractBO.loadPreviewData(connector.getInputConfig(), connector.getCohortId(),
                    SAMPLE_ROWS, connector.getMergeConfig(), connector.getPivotConfig(),
                    connector.getUploadInfo())) {
                return TableSample.from(data, SAMPLE_ROWS);
            }
        });
    }

    private TableSample applyTransformer(TableSample preview, ConnectorTransformerDTO transformer, String patientIdColumn) {
        List<Map<String, Object>> rows = transformationBO.applyBuiltInTransformation(
                transformer, new ArrayList<>(preview.toTableData().getRows()), patientIdColumn);
        List<String> columns = new ArrayList<>(preview.getColumns());
        rows.forEach(row -> row.keySet().stream().filter(column -> !columns.contains(column)).forEach(columns::add));
        return new TableSample(columns, rows, preview.getColumnProfiles());
    }
}
