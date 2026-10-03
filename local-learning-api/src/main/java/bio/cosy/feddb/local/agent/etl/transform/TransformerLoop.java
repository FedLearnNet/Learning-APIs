package bio.cosy.feddb.local.agent.etl.transform;

import bio.cosy.feddb.core.api.datamodler.schema.SchemaNodeType;
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
        TableSample preview = QuarkusTransaction.joiningExisting().call(() -> {
            try (TableData data = extractBO.loadPreviewData(connector.getInputConfig(), connector.getCohortId(),
                    config.connector().previewRows(), connector.getMergeConfig(), connector.getPivotConfig(),
                    connector.getUploadInfo())) {
                return TableSample.from(data, config.connector().previewRows());
            }
        });
        return select(connector, preview);
    }

    public ConnectorDTO select(ConnectorDTO connector, TableSample previewData) {
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
            ConnectorSelectedTransformer selected =
                    transformerOrganizer.selectTransformer(preview, schemaNode, patientIdColumn);
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
