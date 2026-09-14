package bio.cosy.feddb.local.api.importer.connector;

import bio.cosy.feddb.local.api.importer.files.ConnectorFileUploadInfoDTO;
import bio.cosy.feddb.local.api.importer.files.table.ColumnNames;
import bio.cosy.feddb.local.api.importer.mapping.ConnectorMappingDTO;
import bio.cosy.feddb.local.api.importer.mapping.ConnectorValueMappingConfigDTO;
import bio.cosy.feddb.local.api.importer.transformer.ConnectorTransformerEntity;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class ConnectorHelper {


    public static List<String> filterOurUnsuedColumns(
            ConnectorEntity connectorEntity,
            List<ConnectorFileUploadInfoDTO> uploadInfo
    ) {
        List<String> used = getUsedColumns(connectorEntity);
        List<String> filterable = uploadColumns(connectorEntity, uploadInfo);
        return filterOurUnsuedColumns(used, filterable);
    }

    public static List<String> getUsedColumns(ConnectorEntity connectorEntity) {
        if (connectorEntity == null) {
            return List.of();
        }

        LinkedHashSet<String> used = new LinkedHashSet<>();
        if (connectorEntity.getSchemaMapping() != null) {
            connectorEntity.getSchemaMapping().stream()
                    .filter(Objects::nonNull)
                    .forEach(mapping -> addMappingColumns(used, mapping));
        }
        mergeColumns(connectorEntity).forEach((table, column) ->
                addColumn(used, ColumnNames.qualify(table, column)));

        List<ConnectorTransformerEntity> transformers = connectorEntity.getTransformers();
        if (transformers == null) {
            return List.copyOf(used);
        }
        for (int index = transformers.size() - 1; index >= 0; index--) {
            ConnectorTransformerEntity transformer = transformers.get(index);
            if (transformer == null) {
                continue;
            }
            Set<String> outputs = transformerOutputs(transformer);
            if (!outputs.isEmpty() && used.stream().noneMatch(column -> matchesAny(column, outputs))) {
                continue;
            }
            if (!outputs.isEmpty()) {
                used.removeIf(column -> matchesAny(column, outputs));
            }
            transformerInputs(transformer).forEach(column -> addColumn(used, column));
        }
        return List.copyOf(used);
    }

    public static List<String> filterOurUnsuedColumns(List<String> used, List<String> filterable) {
        if (used == null || used.isEmpty() || filterable == null || filterable.isEmpty()) {
            return List.of();
        }
        List<String> usedColumns = used.stream()
                .filter(Objects::nonNull)
                .map(ColumnNames::column)
                .filter(column -> !column.isEmpty())
                .toList();
        return filterable.stream()
                .filter(Objects::nonNull)
                .filter(column -> usedColumns.stream().anyMatch(usedColumn -> ColumnNames.matches(usedColumn, column)))
                .distinct()
                .toList();
    }

    private static void addMappingColumns(Set<String> used, ConnectorMappingDTO mapping) {
        addColumn(used, mapping.getColumn());
        addColumn(used, mapping.getVisitIdMapping());
        addColumn(used, mapping.getVisitTimestampMapping());
        ConnectorValueMappingConfigDTO valueMapping = mapping.getValueMappingConfig();
        if (valueMapping != null) {
            addColumn(used, valueMapping.getMappingColumn());
            addColumn(used, valueMapping.getValueColumn());
        }
    }

    private static Set<String> transformerOutputs(ConnectorTransformerEntity transformer) {
        LinkedHashSet<String> outputs = new LinkedHashSet<>();
        Map<String, Object> returnMapping = transformer.getReturnMapping();
        if (returnMapping == null || returnMapping.isEmpty()) {
            addCommaSeparated(outputs, transformer.getColumn());
        } else {
            returnMapping.values().forEach(value -> addMappedColumns(outputs, value));
        }
        return outputs;
    }

    private static Set<String> transformerInputs(ConnectorTransformerEntity transformer) {
        LinkedHashSet<String> inputs = new LinkedHashSet<>();
        Map<String, Object> returnMapping = transformer.getReturnMapping();
        if (returnMapping == null || returnMapping.isEmpty()) {
            addCommaSeparated(inputs, transformer.getColumn());
        }
        if (transformer.getInputMapping() != null) {
            transformer.getInputMapping().values().forEach(value -> {
                if ("[VALUE]".equals(value)) {
                    addCommaSeparated(inputs, transformer.getColumn());
                } else if (!(value instanceof String text) || !text.startsWith("[VALUE]")) {
                    addMappedColumns(inputs, value);
                }
            });
        }
        return inputs;
    }

    private static List<String> uploadColumns(
            ConnectorEntity connector,
            List<ConnectorFileUploadInfoDTO> uploadInfo
    ) {
        if (uploadInfo == null) {
            return List.of();
        }
        Map<String, String> mergeColumns = mergeColumns(connector);
        List<String> columns = new ArrayList<>();
        uploadInfo.stream()
                .filter(Objects::nonNull)
                .forEach(info -> {
                    if (info.getColumns() == null) {
                        return;
                    }
                    String table = ColumnNames.table(info.getSheet() == null ? "table" : info.getSheet());
                    List<String> tableColumns = info.getColumns().stream()
                            .filter(Objects::nonNull)
                            .map(ColumnNames::column)
                            .map(ColumnNames::unqualify)
                            .filter(column -> !column.isEmpty())
                            .toList();
                    if (!mergeColumns.isEmpty()
                            && (!mergeColumns.containsKey(table) || tableColumns.size() == 1)) {
                        return;
                    }
                    tableColumns.stream()
                            .map(column -> ColumnNames.qualify(table, column))
                            .forEach(columns::add);
                });
        return List.copyOf(columns);
    }

    /** Configured source UID column per table participating in a merge. */
    private static Map<String, String> mergeColumns(ConnectorEntity connector) {
        if (connector == null
                || connector.getMergeConfig() == null
                || connector.getMergeConfig().getSheetUidMapping() == null
                || connector.getMergeConfig().getSheetUidMapping().isEmpty()) {
            return Map.of();
        }
        Map<String, String> columns = new LinkedHashMap<>();
        connector.getMergeConfig().getSheetUidMapping().forEach((table, column) -> {
            String normalizedTable = ColumnNames.table(table);
            String normalizedColumn = ColumnNames.column(ColumnNames.unqualify(column));
            if (!normalizedTable.isEmpty() && !normalizedColumn.isEmpty()) {
                columns.put(normalizedTable, normalizedColumn);
            }
        });
        return columns;
    }

    private static void addCommaSeparated(Set<String> columns, String value) {
        if (value == null) {
            return;
        }
        for (String column : value.split(",")) {
            addColumn(columns, column);
        }
    }

    private static void addMappedColumns(Set<String> columns, Object value) {
        if (value instanceof Collection<?> collection) {
            collection.forEach(item -> addMappedColumns(columns, item));
        } else if (value instanceof Object[] array) {
            for (Object item : array) {
                addMappedColumns(columns, item);
            }
        } else if (value instanceof String column) {
            addColumn(columns, column);
        }
    }

    private static void addColumn(Set<String> columns, String value) {
        String column = ColumnNames.column(value);
        if (!column.isEmpty()) {
            columns.add(column);
        }
    }

    private static boolean matchesAny(String column, Set<String> candidates) {
        return candidates.stream().anyMatch(candidate -> ColumnNames.matches(column, candidate));
    }
}
