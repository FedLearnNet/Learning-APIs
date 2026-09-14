package bio.cosy.feddb.local.api.importer.files;

import bio.cosy.feddb.core.api.file.analytics.ColumnProfile;
import bio.cosy.feddb.core.api.file.analytics.FileProfile;
import bio.cosy.feddb.core.api.file.analytics.ShardedRowProfiler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Profiling one table is spread over threads by splitting its columns, not its rows.
 *
 * <p>That choice is what makes the result independent of the thread count: every aggregator still
 * sees every value of its column, in row order, from one thread. Splitting by row would mean merging
 * partial sketches instead, and a file would profile differently depending on the machine it ran on.
 * These tests are what keeps that property from being optimised away.</p>
 */
class ShardedProfilingTest {

    private static final List<String> COLUMNS =
            List.of("patient_id", "item", "charttime", "valuenum", "note", "flag", "rare");

    private static final int ROWS = 40_000;

    @ParameterizedTest
    @ValueSource(ints = {2, 3, 4, 8, 16})
    void producesTheSameProfileWhateverTheThreadCount(int workers) {
        assertEquals(describe(profile(1)), describe(profile(workers)),
                "profile with " + workers + " threads");
    }

    /** More threads than columns must not lose a column or silently drop its values. */
    @Test
    void keepsEveryColumnWhenThreadsOutnumberThem() {
        FileProfile profile = profile(16);

        assertEquals(COLUMNS.size(), profile.getColumns().size());
        assertEquals(COLUMNS, profile.getColumns().stream().map(ColumnProfile::name).toList());
        assertEquals(ROWS, profile.getRowsScanned());
    }

    /** Rows shorter than the column list count as missing, the same as on the sequential path. */
    @Test
    void treatsShortRowsAsMissingOnEveryThread() {
        FileProfile sequential = profileRagged(1);
        FileProfile sharded = profileRagged(4);

        assertEquals(describe(sequential), describe(sharded));
        assertEquals(ROWS, sharded.getColumns().getLast().missing(),
                "a column no row reaches is missing everywhere");
    }

    private static FileProfile profile(int workers) {
        try (ShardedRowProfiler profiler = ShardedRowProfiler.of(COLUMNS, null, workers)) {
            for (int i = 0; i < ROWS; i++) {
                profiler.accept(row(i));
            }
            return profiler.finish();
        }
    }

    private static FileProfile profileRagged(int workers) {
        try (ShardedRowProfiler profiler = ShardedRowProfiler.of(COLUMNS, null, workers)) {
            for (int i = 0; i < ROWS; i++) {
                String[] full = row(i);
                profiler.accept(java.util.Arrays.copyOf(full, full.length - 1));
            }
            return profiler.finish();
        }
    }

    /** Mixed types and cardinalities, so both the exact and the sketch-backed paths are exercised. */
    private static String[] row(int i) {
        return new String[]{
                "P" + (i % 900),                                   // text, low cardinality
                String.valueOf(50800 + (i % 250)),                 // integer
                "2024-03-0" + (1 + (i % 9)) + "T08:15:00",         // datetime
                i % 37 == 0 ? "" : String.valueOf(i / 10.0),       // decimal with gaps
                "note-" + i,                                       // text, past the exact budget
                i % 2 == 0 ? "true" : "false",                     // boolean
                i % 1000 == 0 ? "sentinel" : null,                 // mostly missing
        };
    }

    private static List<String> describe(FileProfile profile) {
        List<String> described = new ArrayList<>();
        for (ColumnProfile column : profile.getColumns()) {
            described.add(String.join("|",
                    column.name(),
                    column.type(),
                    String.valueOf(column.count()),
                    String.valueOf(column.missing()),
                    String.valueOf(column.uniqueValues()),
                    String.valueOf(column.mean()),
                    String.valueOf(column.std()),
                    String.valueOf(column.min()),
                    String.valueOf(column.p25()),
                    String.valueOf(column.median()),
                    String.valueOf(column.p75()),
                    String.valueOf(column.max()),
                    String.valueOf(column.topCategories()),
                    String.valueOf(column.valueCounts())));
        }
        return described;
    }
}
