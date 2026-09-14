package bio.cosy.feddb.local.api.importer.files.table;

import bio.cosy.feddb.local.api.importer.files.ConnectorFileUploadInfoDTO;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;


public final class ParsedTables implements AutoCloseable {

    private final Map<String, TableData> tables;

    private ParsedTables(Map<String, TableData> tables) {
        this.tables = tables == null ? new LinkedHashMap<>() : new LinkedHashMap<>(tables);
    }

    public static ParsedTables empty() {
        return new ParsedTables(Map.of());
    }

    public static ParsedTables single(TableData tableData) {
        return tableData == null
                ? empty()
                : new ParsedTables(Map.of(ConnectorFileUploadInfoDTO.DEFAULT_SHEET_NAME, tableData));
    }

    public static ParsedTables of(Map<String, TableData> tables) {
        return new ParsedTables(tables);
    }

    public Map<String, TableData> tables() {
        return tables;
    }

    public Collection<TableData> values() {
        return tables.values();
    }

    public boolean isEmpty() {
        return tables.isEmpty();
    }

    public int size() {
        return tables.size();
    }

    public TableData takeFirst() {
        TableData first = null;
        for (TableData tableData : tables.values()) {
            if (first == null) {
                first = tableData;
            } else if (tableData != null) {
                tableData.close();
            }
        }
        tables.clear();
        return first;
    }

    @Override
    public void close() {
        tables.values().stream()
                .filter(java.util.Objects::nonNull)
                .forEach(TableData::close);
        tables.clear();
    }
}
