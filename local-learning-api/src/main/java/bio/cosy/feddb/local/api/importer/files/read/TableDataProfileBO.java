package bio.cosy.feddb.local.api.importer.files.read;

import bio.cosy.feddb.core.api.file.analytics.ColumnProfile;
import bio.cosy.feddb.core.api.file.analytics.FileAnalytics;
import bio.cosy.feddb.core.api.file.analytics.FileProfile;
import bio.cosy.feddb.core.api.file.analytics.RowScanListener;
import bio.cosy.feddb.core.api.file.analytics.ShardedRowProfiler;
import bio.cosy.feddb.local.api.importer.ImportPhaseTimer;
import bio.cosy.feddb.local.api.importer.files.table.TableData;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/** Column statistics for a table, either gathered while it is read or taken afterwards. */
@ApplicationScoped
public class TableDataProfileBO {

    @Inject
    FileAnalytics fileAnalytics;

    public TableDataProfileBO() {
    }

    public TableDataProfileBO(FileAnalytics fileAnalytics) {
        this.fileAnalytics = fileAnalytics;
    }

    /**
     * An accumulator a reader feeds while it parses, so the rows are walked once instead of being
     * written to disk and read back purely to be profiled. Pairs with {@link #attach}.
     *
     * @param source  the table the rows come from, for the progress log
     * @param workers upper bound on profiling threads; one keeps the work on the calling thread
     */
    public ShardedRowProfiler rowProfiler(List<String> columns, String source, int workers) {
        return rowProfiler(columns, source, workers, RowScanListener.NONE);
    }

    /**
     * @param listener told how far the scan has come, for whoever is watching the import
     */
    public ShardedRowProfiler rowProfiler(
            List<String> columns,
            String source,
            int workers,
            RowScanListener listener
    ) {
        return ShardedRowProfiler.of(columns, source, workers, listener);
    }

    /** Attaches a profile gathered by {@link #rowProfiler} while the table was being read. */
    public TableData attach(TableData tableData, FileProfile profile, String source) {
        if (tableData == null) {
            return null;
        }
        try {
            tableData.setColumnProfiles(alignedProfiles(profile, columnsOf(tableData)));
        } catch (Exception e) {
            Log.debugf(e, "Could not attach column profiles for table data from %s",
                    source);
            tableData.setColumnProfiles(new ArrayList<>());
        }
        return tableData;
    }

    /**
     * Second pass over a finished table.
     *
     * <p>Only for tables nobody profiled while building them - a merge, pivot or collapse result,
     * where the rows the profile has to describe do not exist until the operation is done. Readers
     * that produce rows themselves should use {@link #rowProfiler} instead.</p>
     *
     * @param source the file - and table, where applicable - the rows came from. Purely for the phase
     *               logs: profiling is the longest import phase on large files, and without it the
     *               {@code Statistics} lines of concurrent imports cannot be told apart.
     */
    public TableData enrich(TableData tableData, String source) {
        if (tableData == null) {
            return null;
        }
        List<String> columns = columnsOf(tableData);
        String label = source == null || source.isBlank() ? "" : source + " - ";
        ImportPhaseTimer timer = ImportPhaseTimer.started("Statistics",
                "%s%d columns over %d rows", label, columns.size(), tableData.longSize());
        try (Stream<Map<String, Object>> rows = tableData.streamRows()) {
            FileProfile profile = fileAnalytics.profileRows(columns, rows, source);
            tableData.setColumnProfiles(alignedProfiles(profile, columns));
            timer.done("%s%d columns over %d rows", label, tableData.getColumnProfiles().size(),
                    tableData.longSize());
        } catch (Exception e) {
            Log.debugf(e, "Could not build column profiles for table data from %s", label);
            tableData.setColumnProfiles(new ArrayList<>());
            timer.failed(e);
        }
        return tableData;
    }

    /** The profiles of a finished table, for callers that want them without keeping the table. */
    public List<ColumnProfile> profiles(TableData tableData) {
        TableData profiled = enrich(tableData, null);
        return profiled == null || profiled.getColumnProfiles() == null
                ? List.of()
                : profiled.getColumnProfiles();
    }

    private static List<String> columnsOf(TableData tableData) {
        return tableData.getColumns() == null ? List.of() : tableData.getColumns();
    }

    /**
     * Re-labels the profiles with the table's own column names. The profiler is fed positionally, so
     * its names come from whatever list it was created with; the table is the authority on naming.
     */
    private static List<ColumnProfile> alignedProfiles(FileProfile profile, List<String> columns) {
        List<ColumnProfile> profiledColumns =
                profile == null || profile.getColumns() == null ? List.of() : profile.getColumns();
        List<ColumnProfile> aligned = new ArrayList<>(profiledColumns.size());
        for (int index = 0; index < profiledColumns.size(); index++) {
            ColumnProfile column = profiledColumns.get(index);
            aligned.add(index < columns.size() ? column.withName(columns.get(index)) : column);
        }
        return aligned;
    }
}
