package bio.cosy.feddb.local.agent.etl.transform.organizer;

import bio.cosy.feddb.core.api.file.analytics.ColumnProfile;
import bio.cosy.feddb.local.agent.etl.transform.assistant.TransformerAssistantMessageDTO;
import bio.cosy.feddb.local.agent.etl.transform.model.ColumnCandidate;
import bio.cosy.feddb.local.agent.etl.transform.model.ConnectorSelectedTransformer;
import bio.cosy.feddb.local.agent.etl.transform.tools.TransformerListTool;
import bio.cosy.feddb.local.api.importer.connector.ConnectorDTO;
import bio.cosy.feddb.local.api.importer.extract.ConnectorExtractBO;
import bio.cosy.feddb.local.api.importer.files.table.TableData;
import bio.cosy.feddb.local.api.importer.files.table.TableSample;
import bio.cosy.feddb.local.api.importer.mapping.ConnectorMappingDTO;
import bio.cosy.feddb.local.api.importer.mapping.MappingBO;
import bio.cosy.feddb.local.api.importer.statistics.TransformedStatisticsBO;
import bio.cosy.feddb.local.api.importer.transformer.ConnectorTransformerBO;
import bio.cosy.feddb.local.api.importer.transformer.ConnectorTransformerDTO;
import bio.cosy.feddb.local.api.schema.schemanode.LocalSchemaNodeWithPathDTO;
import bio.cosy.feddb.local.api.schema.schemanode.SchemaNodeBO;
import io.quarkus.logging.Log;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

import static bio.cosy.feddb.local.agent.etl.transform.assistant.TransformerAssistantMessageType.*;

@ApplicationScoped
public class TransformerLoop {

    static final int SAMPLE_ROWS = 200;

    @Inject
    TransformerOrganizer transformerOrganizer;

    @Inject
    ColumnMatcher columnMatcher;

    @Inject
    TransformerListTool transformerListTool;

    @Inject
    TransformedStatisticsBO statisticsBO;

    @Inject
    ConnectorExtractBO extractBO;

    @Inject
    ConnectorTransformerBO transformerBO;

    @Inject
    SchemaNodeBO schemaNodeBO;

    @Inject
    MappingBO mappingBO;

    public void run(ConnectorDTO connector,
                    Consumer<TransformerAssistantMessageDTO> events,
                    Supplier<String> instructions,
                    UnaryOperator<String> askUser) {

        List<LocalSchemaNodeWithPathDTO> schemaNodes = getSchemaNodesTransactional(connector);
        List<ConnectorMappingDTO> mappings = connector.getSchemaMapping();
        String patientIdColumn = mappingBO.resolveExternalIdSourceColumn(mappings);
        transformerListTool.resetStoreTools();
        TableSample preview = loadPreview(connector);
        Map<String, ColumnProfile> fileProfiles = fileProfiles(connector);
        Map<Long, List<ColumnCandidate>> candidates = columnMatcher.match(preview, schemaNodes, fileProfiles);

        schemaNodes.forEach(node ->
                events.accept(TransformerAssistantMessageDTO.node(NODE_PENDING, node.getId(), node.getPath(), null)));
        for (LocalSchemaNodeWithPathDTO node : schemaNodes) {
            runForOneSchemaNode(node, preview, patientIdColumn, candidates, fileProfiles, instructions, events, askUser);
        }
    }

    public void runForOneSchemaNode(LocalSchemaNodeWithPathDTO node,
                                    TableSample preview,
                                    String patientIdColumn,
                                    Map<Long, List<ColumnCandidate>> candidates,
                                    Map<String, ColumnProfile> fileProfiles,
                                    Supplier<String> instructions,
                                    Consumer<TransformerAssistantMessageDTO> events,
                                    UnaryOperator<String> askUser
    ) {
        events.accept(TransformerAssistantMessageDTO.node(NODE_STARTED, node.getId(), node.getPath(), null));

        ConnectorSelectedTransformer selected = transformerOrganizer.selectTransformer(preview, node,
                patientIdColumn, instructions.get(), askUser, candidates.getOrDefault(node.getId(), List.of()),
                fileProfiles, node.getDataType());
        events.accept(TransformerAssistantMessageDTO.node(NODE_RESULT, node.getId(), node.getPath(), selected));
        for (ConnectorTransformerDTO transformer : selected.getSelectedTransformers()) {
            preview = applyTransformer(preview, transformer, patientIdColumn);
        }
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
                .collect(Collectors.toList());
    }

    private Map<String, ColumnProfile> fileProfiles(ConnectorDTO connector) {
        try {
            return QuarkusTransaction.joiningExisting().call(() -> statisticsBO.getTransformedStatistics(
                            connector.getCohortId(), connector.getInputConfig(), connector.getUploadInfo(),
                            connector.getMergeConfig(), connector.getPivotConfig(), connector.getTransformer(),
                            connector.getSchemaMapping()))
                    .stream()
                    .filter(Objects::nonNull)
                    .collect(Collectors.toMap(ColumnProfile::name, profile -> profile, (a, b) -> a));
        } catch (RuntimeException e) {
            Log.infof("No file statistics for the transformer selection, validating the sample only: %s", e.getMessage());
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
        List<Map<String, Object>> rows = transformerBO.applyBuiltInTransformation(
                transformer, new ArrayList<>(preview.toTableData().getRows()), patientIdColumn);
        List<String> columns = new ArrayList<>(preview.getColumns());
        rows.forEach(row -> row.keySet().stream().filter(column -> !columns.contains(column)).forEach(columns::add));
        return new TableSample(columns, rows, preview.getColumnProfiles());
    }
}
