package bio.cosy.feddb.local.agent.etl.transform;

import bio.cosy.feddb.core.api.datamodler.schema.SchemaNodeType;
import bio.cosy.feddb.local.agent.etl.transform.assistant.TransformerAssistantMessageDTO;
import bio.cosy.feddb.local.agent.etl.transform.model.ConnectorSelectedTransformer;
import bio.cosy.feddb.local.api.importer.connector.ConnectorDTO;
import bio.cosy.feddb.local.api.importer.extract.ConnectorExtractBO;
import bio.cosy.feddb.local.api.importer.files.table.TableData;
import bio.cosy.feddb.local.api.importer.files.table.TableSample;
import bio.cosy.feddb.local.api.importer.mapping.ConnectorMappingDTO;
import bio.cosy.feddb.local.api.importer.mapping.MappingBO;
import bio.cosy.feddb.local.api.importer.transformer.ConnectorTransformerBO;
import bio.cosy.feddb.local.api.importer.transformer.ConnectorTransformerDTO;
import bio.cosy.feddb.local.api.schema.LocalSchemaNodeNestedDTO;
import bio.cosy.feddb.local.api.schema.schemanode.SchemaNodeBO;
import bio.cosy.feddb.local.config.FLNetClientConfig;
import io.quarkus.logging.Log;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

import static bio.cosy.feddb.local.agent.etl.transform.assistant.TransformerAssistantMessageType.NODE_RESULT;
import static bio.cosy.feddb.local.agent.etl.transform.assistant.TransformerAssistantMessageType.NODE_STARTED;

@ApplicationScoped
public class TransformerLoop {

    @Inject
    TransformerOrganizer transformerOrganizer;

    @Inject
    ConnectorExtractBO extractBO;

    @Inject
    ConnectorTransformerBO transformerBO;

    @Inject
    SchemaNodeBO schemaNodeBO;

    @Inject
    MappingBO mappingBO;

    @Inject
    FLNetClientConfig config;

    public ConnectorDTO selectAfterMerge(ConnectorDTO connector) {
        return select(connector, loadPreview(connector));
    }

    public ConnectorDTO select(ConnectorDTO connector, TableSample previewData) {
        return select(connector, previewData, event -> {
        }, () -> "", null);
    }

    public ConnectorDTO selectAfterMerge(ConnectorDTO connector,
                                         Consumer<TransformerAssistantMessageDTO> events,
                                         Supplier<String> instructions,
                                         UnaryOperator<String> askUser) {
        return select(connector, loadPreview(connector), events, instructions, askUser);
    }

    public ConnectorDTO select(ConnectorDTO connector,
                               TableSample previewData,
                               Consumer<TransformerAssistantMessageDTO> events,
                               Supplier<String> instructions,
                               UnaryOperator<String> askUser) {
        Map<String, LocalSchemaNodeNestedDTO> schemaNodes = QuarkusTransaction.joiningExisting().call(() -> {
            Map<String, LocalSchemaNodeNestedDTO> nodes = new LinkedHashMap<>();
            collectAttributes(schemaNodeBO.getSchemaNodesNestedForCohort(connector.getCohortId()).getChildNodes(), null, nodes);
            return nodes;
        });

        List<ConnectorMappingDTO> mappings = connector.getSchemaMapping() == null
                ? new ArrayList<>() : new ArrayList<>(connector.getSchemaMapping());
        List<ConnectorTransformerDTO> transformers = connector.getTransformer() == null
                ? new ArrayList<>() : new ArrayList<>(connector.getTransformer());
        String patientIdColumn = mappingBO.resolveExternalIdSourceColumn(mappings);
        Set<Long> mapped = mappings.stream().map(ConnectorMappingDTO::getSchemaId).filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());

        TableSample preview = previewData;
        for (Map.Entry<String, LocalSchemaNodeNestedDTO> entry : schemaNodes.entrySet()) {
            LocalSchemaNodeNestedDTO schemaNode = entry.getValue();
            if (mapped.contains(schemaNode.getId())) {
                continue;
            }
            events.accept(TransformerAssistantMessageDTO.node(NODE_STARTED, schemaNode.getId(), entry.getKey(), null));
            ConnectorSelectedTransformer selected = transformerOrganizer.selectTransformer(
                    preview, schemaNode, patientIdColumn, instructions.get(), askUser);
            events.accept(TransformerAssistantMessageDTO.node(NODE_RESULT, schemaNode.getId(), entry.getKey(), selected));
            if (!selected.isFoundTransformer()) {
                Log.infof("No transformer for schema node %s: %s", entry.getKey(), selected.getNeededTransformerFunction());
                continue;
            }
            ConnectorTransformerDTO transformer = selected.getSelectedTransformer();
            if (transformer != null) {
                transformer.setConnectorId(connector.getId());
                transformer.setPosition(transformers.size() + 1);
                transformers.add(transformer);
                preview = applyTransformer(preview, transformer, patientIdColumn);
            }
            ConnectorMappingDTO mapping = new ConnectorMappingDTO();
            mapping.setColumn(selected.getMappedColumn());
            mapping.setMapping(entry.getKey());
            mapping.setSchemaId(schemaNode.getId());
            mappings.add(mapping);
        }

        connector.setTransformer(transformers);
        connector.setSchemaMapping(mappings);
        return connector;
    }

    private TableSample loadPreview(ConnectorDTO connector) {
        return QuarkusTransaction.joiningExisting().call(() -> {
            try (TableData data = extractBO.loadPreviewData(connector.getInputConfig(), connector.getCohortId(),
                    config.connector().previewRows(), connector.getMergeConfig(), connector.getPivotConfig(),
                    connector.getUploadInfo())) {
                return TableSample.from(data, config.connector().previewRows());
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

    private void collectAttributes(Set<LocalSchemaNodeNestedDTO> nodes, String parentPath,
                                   Map<String, LocalSchemaNodeNestedDTO> attributes) {
        if (nodes == null) {
            return;
        }
        for (LocalSchemaNodeNestedDTO node : nodes) {
            String path = parentPath == null ? node.getName() : parentPath + "." + node.getName();
            if (node.getType() == SchemaNodeType.GROUP) {
                collectAttributes(node.getChildNodes(), path, attributes);
            } else if (node.getType() == SchemaNodeType.ATOMIC_ATTRIBUTE || node.getType() == SchemaNodeType.LIST_ATTRIBUTE) {
                attributes.put(path, node);
            }
        }
    }
}
