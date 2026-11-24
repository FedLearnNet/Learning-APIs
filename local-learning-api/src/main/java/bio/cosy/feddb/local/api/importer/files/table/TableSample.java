package bio.cosy.feddb.local.api.importer.files.table;

import bio.cosy.feddb.core.api.file.analytics.ColumnProfile;
import bio.cosy.feddb.core.api.file.analytics.RowProfiler;
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

    /**
     * The first {@code headRows} rows, plus for each column still empty in them the first later row
     * with a value (at most {@code maxFillers} rows, searching at most {@code maxScanRows} rows).
     */
    public static TableSample withFillers(final TableData tableData, final int headRows,
                                          final int maxFillers, final int maxScanRows) {
        if (tableData == null) {
            return null;
        }
        List<String> columns = tableData.getColumns() == null ? List.of() : tableData.getColumns();
        Set<String> empty = new LinkedHashSet<>(columns);
        List<Map<String, Object>> sampledRows = new ArrayList<>();
        int fillers = 0;
        int scanned = 0;
        try (var rows = tableData.streamRows()) {
            Iterator<Map<String, Object>> iterator = rows.iterator();
            while (iterator.hasNext()) {
                Map<String, Object> row = iterator.next();
                if (sampledRows.size() < headRows) {
                    sampledRows.add(row);
                    empty.removeIf(column -> hasValue(row, column));
                } else if (empty.isEmpty() || fillers >= maxFillers || scanned++ >= maxScanRows) {
                    break;
                } else if (empty.removeIf(column -> hasValue(row, column))) {
                    sampledRows.add(row);
                    fillers++;
                }
            }
        }
        return new TableSample(columns, sampledRows, tableData.getColumnProfiles());
    }

    /** Whether a row has a value in a column; blank, NA, null, ... and {@code ?} count as empty. */
    public static boolean hasValue(final Map<String, Object> row, final String column) {
        Object value = row == null ? null : row.get(column);
        if (value == null) {
            return false;
        }
        String text = String.valueOf(value);
        return !RowProfiler.isMissing(text) && !"?".equals(text.trim());
    }
}
