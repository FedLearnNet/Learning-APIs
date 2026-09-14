package bio.cosy.feddb.local.api.importer.files.read;

import bio.cosy.feddb.core.api.file.analytics.ColumnProfile;
import bio.cosy.feddb.core.api.file.analytics.RowScanListener;
import bio.cosy.feddb.local.api.importer.extract.SheetMergeResultDTO;
import bio.cosy.feddb.local.api.importer.files.progress.ImportProgressTracker;
import bio.cosy.feddb.local.api.importer.files.progress.ImportTableDTO;
import bio.cosy.feddb.local.api.importer.files.table.SheetMergePlan;
import bio.cosy.feddb.local.api.importer.files.table.TableData;
import bio.cosy.feddb.local.api.importer.files.table.TableDataTempFiles;
import bio.cosy.feddb.local.config.FLNetClientConfig;
import io.quarkus.logging.Log;
import jakarta.inject.Inject;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * What every table reader needs from its surroundings: where the rows go while they are being read,
 * how many threads it may use, how a merge is applied to what it read, and how to name the input in
 * the phase logs.
 *
 * <p>These are decisions about the import as a whole rather than about one file format, so they are
 * answered once here instead of by each reader for itself.</p>
 */
public abstract class TableReaderSupport {

    @Inject
    FLNetClientConfig config;

    @Inject
    TableDataProfileBO profiles;

    @Inject
    TableDataMergeBO merges;

    @Inject
    ImportProgressTracker progress;

    protected TableReaderSupport() {
        this.progress = new ImportProgressTracker();
    }

    protected TableReaderSupport(FLNetClientConfig config, TableDataProfileBO profiles, TableDataMergeBO merges) {
        this();
        this.config = config;
        this.profiles = profiles;
        this.merges = merges;
    }

    /** Announces the tables a file holds, so all of them can be shown before any is read. */
    protected void reportTables(TableReadSpec spec, List<String> names) {
        progress.tablesFound(spec.importId(), names);
    }

    /** Reports one table's state. Called from the threads reading them, several at a time. */
    protected void reportTable(TableReadSpec spec, ImportTableDTO table) {
        progress.table(spec.importId(), table);
    }

    /**
     * Reports a table's rows as they are measured.
     *
     * <p>Reading a table of millions of rows is minutes in which nothing else about the import
     * changes, so the count the profiler already keeps for the log is passed on as it goes. Costs
     * nothing when nobody is watching: the profiler only calls this every couple of seconds, and an
     * untracked import never gets past the first line of {@code table}.</p>
     */
    protected RowScanListener rowReporter(TableReadSpec spec, String table, int position, int total,
                                          List<String> columns) {
        if (spec.importId() == null) {
            return RowScanListener.NONE;
        }
        int columnCount = columns == null ? 0 : columns.size();
        return (rows, elapsedSeconds, rowsPerSecond) -> reportTable(spec,
                ImportTableDTO.reading(table, position, total, rows, columnCount, rowsPerSecond));
    }

    /** Empty cells across a finished table, from the statistics gathered while it was read. */
    protected static Long missingValues(TableData table) {
        if (table == null || table.getColumnProfiles() == null || table.getColumnProfiles().isEmpty()) {
            return null;
        }
        long missing = 0L;
        for (ColumnProfile profile : table.getColumnProfiles()) {
            missing += profile.missing();
        }
        return missing;
    }

    /**
     * A table to read rows into: on the heap when the caller asked for a bounded number of rows, on
     * disk otherwise, because a full read has no upper bound the heap could be sized for.
     */
    protected TableData createTable(List<String> columns, TableReadSpec spec) {
        return spec.maxRows() != null
                ? new TableData(columns, new ArrayList<>(), new ArrayList<>())
                : TableData.diskBacked(columns, new ArrayList<>(), workDirectory());
    }

    /** Moves a growing in-memory table to disk once it has outgrown the configured row limit. */
    protected void spillIfNeeded(TableData table, TableReadSpec spec) {
        if (table == null || table.isDiskBacked() || spec.maxRows() != null) {
            return;
        }
        int rowLimit = config == null ? 0 : config.connector().importRowLimitForDiskCache();
        if (rowLimit >= 0 && table.longSize() > rowLimit) {
            Log.debugf("Spilling importer rows to disk cache because row count %d exceeded limit %d",
                    table.longSize(), rowLimit);
            table.spillRowsToTempFile(workDirectory());
        }
    }

    /**
     * Merges what was read into one table, profiling the result when statistics were asked for.
     *
     * <p>The profile has to be taken here rather than on the source tables: the rows it describes are
     * the merged ones, which do not exist until this point.</p>
     */
    protected TableData mergeTables(
            Map<String, TableData> tables,
            SheetMergeResultDTO mergeConfig,
            TableReadSpec spec,
            String source
    ) {
        TableData merged = spec.preview()
                ? merges.mergePreview(tables, mergeConfig, spec.maxRows())
                : merges.mergeForRun(tables, mergeConfig);
        return spec.profile() ? profiles.enrich(merged, source) : merged;
    }

    protected Path workDirectory() {
        return TableDataTempFiles.baseDirectory(config);
    }

    /** Tables of one file read at the same time, capped by the cores actually available. */
    protected int parseParallelism() {
        int configured = config == null ? 1 : config.connector().importParseParallelism();
        return Math.max(1, Math.min(configured, Runtime.getRuntime().availableProcessors()));
    }

    /**
     * Profiling threads for one table, given how many tables are being read at the same time. Tables
     * read side by side share the profiling budget, so the two settings together bound the threads an
     * import uses rather than multiplying.
     */
    protected int profileParallelism(int concurrentTables) {
        int configured = config == null ? 1 : config.connector().importProfileParallelism();
        int available = Math.min(configured, Runtime.getRuntime().availableProcessors());
        return Math.max(1, available / Math.max(1, concurrentTables));
    }

    /** The name of the merged table, for logs and for the sheet the merge is published under. */
    protected static String mergedTableName() {
        return SheetMergePlan.MERGED_TABLE_NAME;
    }

    /**
     * Label for the import phase logs: the file the rows came from, the sheet or ZIP entry within it,
     * and the position in the file's table list so a long multi-table import shows progress.
     */
    protected static String sourceName(File file, String sheet, int index, int total) {
        StringBuilder out = new StringBuilder(file == null ? "unknown file" : file.getName());
        if (sheet != null && !sheet.isBlank()) {
            out.append(" / ").append(sheet);
        }
        return out.append(" (").append(index).append('/').append(total).append(')').toString();
    }

    protected static String sourceName(File file) {
        return sourceName(file, null, 1, 1);
    }

    protected static String sourceName(File file, String sheet) {
        return sourceName(file, sheet, 1, 1);
    }
}
