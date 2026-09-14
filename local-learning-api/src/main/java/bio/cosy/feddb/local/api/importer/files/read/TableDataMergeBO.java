package bio.cosy.feddb.local.api.importer.files.read;

import bio.cosy.feddb.local.api.importer.extract.SheetMergeResultDTO;
import bio.cosy.feddb.local.api.importer.files.table.ParsedTables;
import bio.cosy.feddb.local.api.importer.files.table.SheetMergePlan;
import bio.cosy.feddb.local.api.importer.files.table.TableData;
import bio.cosy.feddb.local.api.importer.files.table.TableDataTempFiles;
import bio.cosy.feddb.local.config.FLNetClientConfig;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Merges the tables of one file into a single table keyed by a common UID.
 *
 * <p>What the merged table looks like is decided by {@link SheetMergePlan}; this class is about
 * getting the rows there, and about the preview variant that folds a UID's rows into one.</p>
 */
@ApplicationScoped
public class TableDataMergeBO {

    @Inject
    FLNetClientConfig config;

    public TableDataMergeBO() {
    }

    public TableDataMergeBO(FLNetClientConfig config) {
        this.config = config;
    }

    public TableData mergePreview(
            Map<String, TableData> tables,
            SheetMergeResultDTO mergeConfig,
            Integer maxRows
    ) {
        TableData merged = merge(tables, mergeConfig, false);
        TableData collapsed = collapse(merged, SheetMergePlan.commonUidColumn(mergeConfig), maxRows);
        if (collapsed != merged && merged != null) {
            merged.close();
        }
        return collapsed;
    }

    public TableData mergeForRun(Map<String, TableData> tables, SheetMergeResultDTO mergeConfig) {
        return merge(tables, mergeConfig, true);
    }

    /** Folds the rows sharing a UID into one row, for a preview that shows a patient per line. */
    public TableData collapse(TableData merged, String uidColumn, Integer maxRows) {
        if (merged == null || merged.longSize() == 0) {
            return merged == null ? TableData.empty() : merged;
        }
        if (uidColumn == null || uidColumn.isBlank() || !merged.getColumns().contains(uidColumn)) {
            return maxRows == null ? merged : merged.limit(maxRows);
        }

        Map<Object, Map<String, Object>> rowsByUid = new LinkedHashMap<>();
        try (Stream<Map<String, Object>> rows = merged.streamRows()) {
            rows.forEach(row -> collapseRow(row, merged.getColumns(), uidColumn, maxRows, rowsByUid));
        }
        return new TableData(
                new ArrayList<>(merged.getColumns()),
                new ArrayList<>(rowsByUid.values()),
                new ArrayList<>()
        );
    }

    private TableData merge(
            Map<String, TableData> tables,
            SheetMergeResultDTO mergeConfig,
            boolean diskBacked
    ) {
        if (tables == null || tables.isEmpty()) {
            return TableData.empty();
        }
        if (!SheetMergePlan.requested(mergeConfig)) {
            return ParsedTables.of(tables).takeFirst();
        }

        try {
            SheetMergePlan plan = SheetMergePlan.of(mergeConfig, columnsByTable(tables));
            TableData result = diskBacked
                    ? TableData.diskBacked(plan.columns(), new ArrayList<>(), workDirectory())
                    : new TableData(plan.columns(), new ArrayList<>(), new ArrayList<>());

            tables.forEach((name, table) -> appendTable(result, name, table, plan));
            Log.infof("Merged %d tables into %d rows and %d columns",
                    tables.size(), result.longSize(), result.getColumns().size());
            return result;
        } finally {
            tables.values().stream().filter(Objects::nonNull).forEach(TableData::close);
        }
    }

    private void appendTable(TableData target, String name, TableData source, SheetMergePlan plan) {
        List<String> columns = source == null ? null : source.getColumns();
        SheetMergePlan.RowBuilder builder = plan.rowBuilder(name, columns);
        if (builder == null) {
            return;
        }
        try (Stream<Map<String, Object>> rows = source.streamRows()) {
            Iterator<Map<String, Object>> iterator = rows.iterator();
            while (iterator.hasNext()) {
                Map<String, Object> row = iterator.next();
                if (row == null) {
                    continue;
                }
                Map<String, Object> merged = builder.build(index -> row.get(columns.get(index)));
                if (merged != null) {
                    target.appendRow(merged);
                }
            }
        }
    }

    private static Map<String, List<String>> columnsByTable(Map<String, TableData> tables) {
        Map<String, List<String>> columns = new LinkedHashMap<>();
        tables.forEach((name, table) -> columns.put(name, table == null ? List.of() : table.getColumns()));
        return columns;
    }

    private void collapseRow(
            Map<String, Object> row,
            List<String> columns,
            String uidColumn,
            Integer maxRows,
            Map<Object, Map<String, Object>> rowsByUid
    ) {
        if (row == null) {
            return;
        }
        Object uid = row.get(uidColumn);
        if (uid == null || uid.toString().isBlank()) {
            return;
        }
        if (!rowsByUid.containsKey(uid) && maxRows != null && rowsByUid.size() >= maxRows) {
            return;
        }
        Map<String, Object> result = rowsByUid.computeIfAbsent(uid, ignored -> {
            Map<String, Object> emptyRow = new LinkedHashMap<>();
            columns.forEach(column -> emptyRow.put(column, null));
            emptyRow.put(uidColumn, uid);
            return emptyRow;
        });
        columns.forEach(column -> {
            if (result.get(column) == null && row.get(column) != null) {
                result.put(column, row.get(column));
            }
        });
    }

    private Path workDirectory() {
        return TableDataTempFiles.baseDirectory(config);
    }
}
