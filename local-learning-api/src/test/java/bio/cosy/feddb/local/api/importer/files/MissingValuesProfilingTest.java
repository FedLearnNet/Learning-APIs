package bio.cosy.feddb.local.api.importer.files;

import bio.cosy.feddb.core.api.file.analytics.ColumnProfile;
import bio.cosy.feddb.core.api.file.analytics.RowProfiler;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Texts like 'NULL' still count as missing, but the profile keeps them, so a mapping can see and replace them. */
class MissingValuesProfilingTest {

    @Test
    void keepsTheTextsCountedAsMissing() {
        ColumnProfile profile = profile("Norm", "  none ", "  none ", "NULL", "", null);

        assertEquals(5, profile.missing());
        assertEquals(List.of(Map.entry("  none ", 2), Map.entry("NULL", 1)), profile.missingValues());
        assertEquals(List.of(Map.entry("Norm", 1)), profile.valueCounts());
    }

    @Test
    void hasNoMissingTextsForBlankCells() {
        assertNull(profile("Norm", "", null).missingValues());
    }

    private static ColumnProfile profile(String... values) {
        RowProfiler profiler = RowProfiler.of(List.of("column"));
        for (String value : values) {
            profiler.startRow();
            profiler.accept(0, value);
            profiler.endRow();
        }
        return profiler.finish().getColumns().getFirst();
    }
}
