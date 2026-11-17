package bio.cosy.feddb.local.api.importer.files.table;

import bio.cosy.feddb.core.api.file.analytics.ColumnProfile;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.*;

/**
 * Immutable JSON-safe table sample. Unlike TableData, it has no temporary-file
 * lifecycle and can therefore be persisted safely in an entity.
 */
@Data
@NoArgsConstructor
public class TableSample {
    private List<String> columns;
    private List<Map<String, Object>> rows;
    private List<ColumnProfile> columnProfiles;

    public TableSample(
            List<String> columns,
            List<Map<String, Object>> rows,
            List<ColumnProfile> columnProfiles
    ) {
        this.columns = columns == null ? List.of() : List.copyOf(columns);
        this.rows = rows == null ? List.of() : rows.stream()
                .map(row -> row == null ? null : Collections.unmodifiableMap(new LinkedHashMap<>(row)))
                .toList();
        this.columnProfiles = columnProfiles == null ? null : List.copyOf(columnProfiles);
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

    public TableSample filterColumns(List<String> keep) {
        List<String> keptColumns = columns.stream()
                .filter(keep::contains)
                .toList();
        List<Map<String, Object>> keptRows = rows.stream()
                .filter(Objects::nonNull)
                .map(row -> {
                    Map<String, Object> filteredRow = new LinkedHashMap<>();
                    keptColumns.forEach(column -> filteredRow.put(column, row.get(column)));
                    return filteredRow;
                })
                .toList();
        List<ColumnProfile> keptProfiles = columnProfiles == null ? null : columnProfiles.stream()
                .filter(profile -> keptColumns.contains(profile.name()))
                .toList();
        return new TableSample(keptColumns, keptRows, keptProfiles);
    }


    public List<String> getColumns() {
        return columns == null ? List.of() : columns;
    }

    @JsonIgnore
    public Map<String, List<Object>> getValues() {
        Map<String, List<Object>> values = new HashMap<>();
        columns.forEach(column -> values.put(column, this.getRows().stream()
                .filter(Objects::nonNull)
                .map(row -> (Object) Objects.toString(row.get(column), ""))
                .distinct()
                .toList()));
        return values;
    }
}
