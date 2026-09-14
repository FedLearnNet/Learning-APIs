package bio.cosy.feddb.local.api.importer.files.table;

import bio.cosy.feddb.core.api.file.analytics.ColumnProfile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Immutable JSON-safe table sample. Unlike TableData, it has no temporary-file
 * lifecycle and can therefore be persisted safely in an entity.
 */
public record TableSample(
        List<String> columns,
        List<Map<String, Object>> rows,
        List<ColumnProfile> columnProfiles
) {
    public TableSample {
        columns = columns == null ? List.of() : List.copyOf(columns);
        rows = rows == null ? List.of() : rows.stream()
                .map(row -> row == null ? null : Collections.unmodifiableMap(new LinkedHashMap<>(row)))
                .toList();
        columnProfiles = columnProfiles == null ? null : List.copyOf(columnProfiles);
    }

    public TableData toTableData() {
        List<Map<String, Object>> mutableRows = rows.stream()
                .<Map<String, Object>>map(row -> row == null ? null : new LinkedHashMap<>(row))
                .toList();
        return new TableData(
                new ArrayList<>(columns),
                new ArrayList<>(mutableRows),
                columnProfiles == null ? null : new ArrayList<>(columnProfiles)
        );
    }

    public static TableSample from(TableData tableData, Integer maxRows) {
        if (tableData == null) {
            return null;
        }
        try (var rows = tableData.streamRows()) {
            List<Map<String, Object>> sampledRows = maxRows == null
                    ? rows.toList()
                    : rows.limit(Math.max(0, maxRows)).toList();
            return new TableSample(
                    tableData.getColumns(),
                    sampledRows,
                    tableData.getColumnProfiles()
            );
        }
    }
}
