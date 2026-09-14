package bio.cosy.feddb.local.api.importer.files.table;

import bio.cosy.feddb.local.api.importer.extract.SheetMergeResultDTO;
import io.quarkus.logging.Log;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.IntFunction;

/**
 * How a set of source tables becomes one merged table: which column carries each table's UID, which
 * column names appear in more than one table and therefore have to be qualified, and the column order
 * of the result.
 *
 * <p>The plan is built once from the merge configuration and the tables' headers. Both merges - the
 * one that streams a ZIP archive's rows straight to disk and the one that walks tables already
 * parsed - decide these questions from this one place, so the same archive produces the same columns
 * whichever path reads it.</p>
 */
public final class SheetMergePlan {

    /** Name of the merged table, and of its UID column where the configuration does not name one. */
    public static final String MERGED_TABLE_NAME = "Merged";
    public static final String DEFAULT_UID_COLUMN = "UID";

    private final String uidColumn;
    /** Resolved UID column per normalized table name; a table without a usable one is absent. */
    private final Map<String, String> uidByTable;
    private final Set<String> duplicateColumns;
    private final List<String> columns;

    private SheetMergePlan(
            String uidColumn,
            Map<String, String> uidByTable,
            Set<String> duplicateColumns,
            List<String> columns
    ) {
        this.uidColumn = uidColumn;
        this.uidByTable = uidByTable;
        this.duplicateColumns = duplicateColumns;
        this.columns = columns;
    }

    /** Whether the configuration asks for a merge at all. */
    public static boolean requested(SheetMergeResultDTO config) {
        return config != null
                && config.getSheetUidMapping() != null
                && !config.getSheetUidMapping().isEmpty();
    }

    /** The column the merged table keys on. */
    public static String commonUidColumn(SheetMergeResultDTO config) {
        String configured = config == null ? null : ColumnNames.column(config.getCommonUidColumnName());
        return configured == null || configured.isBlank() ? DEFAULT_UID_COLUMN : configured;
    }

    /**
     * @param columnsByTable the tables to merge, in output order, with the columns each of them has
     */
    public static SheetMergePlan of(SheetMergeResultDTO config, Map<String, List<String>> columnsByTable) {
        Map<String, String> configured = configuredUids(config);
        Map<String, String> uidByTable = new LinkedHashMap<>();
        columnsByTable.forEach((table, columns) -> {
            String configuredUid = configured.get(ColumnNames.table(table));
            String uid = resolveUid(table, columns, configuredUid);
            if (uid == null || columns == null || !columns.contains(uid)) {
                Log.warnf("Skipping table '%s' during merge: UID column '%s' is not among its columns %s",
                        table, uid, ColumnNames.preview(columns));
                return;
            }
            uidByTable.put(ColumnNames.table(table), uid);
        });

        Set<String> duplicates = duplicateColumns(columnsByTable, uidByTable);
        List<String> merged = new ArrayList<>();
        merged.add(commonUidColumn(config));
        LinkedHashSet<String> rest = new LinkedHashSet<>();
        columnsByTable.forEach((table, columns) -> {
            String uid = uidByTable.get(ColumnNames.table(table));
            if (uid == null) {
                return;
            }
            columns.stream()
                    .filter(column -> !Objects.equals(column, uid))
                    .map(column -> duplicates.contains(column) ? ColumnNames.qualify(table, column) : column)
                    .forEach(rest::add);
        });
        merged.addAll(rest);
        return new SheetMergePlan(commonUidColumn(config), uidByTable, duplicates, merged);
    }

    /** The merged table's column order, its UID column first. */
    public List<String> columns() {
        return columns;
    }

    public String uidColumn() {
        return uidColumn;
    }

    /**
     * A builder for {@code table}'s rows, or {@code null} when the table has no usable UID column and
     * is therefore not part of the merge.
     */
    public RowBuilder rowBuilder(String table, List<String> sourceColumns) {
        String uid = uidByTable.get(ColumnNames.table(table));
        if (uid == null || sourceColumns == null) {
            return null;
        }
        int uidIndex = sourceColumns.indexOf(uid);
        if (uidIndex < 0) {
            return null;
        }
        String[] targets = new String[sourceColumns.size()];
        for (int index = 0; index < targets.length; index++) {
            String column = sourceColumns.get(index);
            targets[index] = index == uidIndex ? null
                    : duplicateColumns.contains(column) ? ColumnNames.qualify(table, column) : column;
        }
        return new RowBuilder(uidColumn, uidIndex, targets);
    }

    private static Map<String, String> configuredUids(SheetMergeResultDTO config) {
        Map<String, String> normalized = new LinkedHashMap<>();
        if (config == null || config.getSheetUidMapping() == null) {
            return normalized;
        }
        config.getSheetUidMapping().forEach((table, uid) -> {
            String column = ColumnNames.column(uid);
            if (table != null && !column.isBlank()) {
                normalized.put(ColumnNames.table(table), column);
            }
        });
        return normalized;
    }

    /**
     * The configured UID as the table actually spells it. A configuration may name the column on its
     * own or qualified with the table it belongs to; both have to find the same column.
     */
    private static String resolveUid(String table, List<String> columns, String configuredUid) {
        if (configuredUid == null || configuredUid.isBlank() || columns == null) {
            return ColumnNames.column(configuredUid);
        }
        String match = ColumnNames.find(columns, configuredUid);
        if (match != null) {
            return match;
        }
        int separator = configuredUid.indexOf(ColumnNames.QUALIFIER);
        if (separator < 0
                || !ColumnNames.table(configuredUid.substring(0, separator)).equals(ColumnNames.table(table))) {
            return configuredUid;
        }
        match = ColumnNames.find(columns, configuredUid.substring(separator + ColumnNames.QUALIFIER.length()));
        return match == null ? configuredUid : match;
    }

    /**
     * Column names carried by more than one of the merged tables, which the merge has to qualify. The
     * UID columns are left out: they all become the one common UID column, so they never collide.
     */
    private static Set<String> duplicateColumns(
            Map<String, List<String>> columnsByTable,
            Map<String, String> uidByTable
    ) {
        Map<String, List<String>> withoutUids = new LinkedHashMap<>();
        columnsByTable.forEach((table, columns) -> {
            String uid = uidByTable.get(ColumnNames.table(table));
            withoutUids.put(table, columns == null ? List.of() : columns.stream()
                    .filter(column -> !Objects.equals(column, uid))
                    .toList());
        });
        return ColumnNames.duplicatesAcross(withoutUids);
    }

    /**
     * Turns one source row into a merged row. The column positions are worked out once per table
     * rather than per row, which is what a merge of tens of millions of rows notices.
     */
    public static final class RowBuilder {

        private final String uidColumn;
        private final int uidIndex;
        /** Merged name per source column position; {@code null} at the UID column, which is renamed. */
        private final String[] targets;

        private RowBuilder(String uidColumn, int uidIndex, String[] targets) {
            this.uidColumn = uidColumn;
            this.uidIndex = uidIndex;
            this.targets = targets;
        }

        /** The merged row, or {@code null} for a row without a UID, which cannot be merged. */
        public Map<String, Object> build(IntFunction<Object> valueAt) {
            Object uid = valueAt.apply(uidIndex);
            if (uid == null || uid.toString().isBlank()) {
                return null;
            }
            Map<String, Object> row = LinkedHashMap.newLinkedHashMap(targets.length);
            row.put(uidColumn, uid);
            for (int index = 0; index < targets.length; index++) {
                if (targets[index] != null) {
                    row.put(targets[index], valueAt.apply(index));
                }
            }
            return row;
        }
    }
}
