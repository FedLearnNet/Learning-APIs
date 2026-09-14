package bio.cosy.feddb.local.api.importer.files;

import bio.cosy.feddb.core.api.file.analytics.ColumnProfile;
import bio.cosy.feddb.core.api.file.analytics.FileAnalytics;
import bio.cosy.feddb.core.api.file.analytics.FileProfile;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the memory bound on column profiling.
 *
 * <p>Profiling used to retain every distinct value, every value frequency and every numeric value
 * per column for the whole file, which put the entire dataset back on the heap even though the rows
 * were streamed off disk. These tests pin both halves of the current behaviour: exact results while
 * a column is small, bounded output once it is not.</p>
 */
class FileAnalyticsBoundedMemoryTest {

    private static final int CAP = Integer.getInteger("feddb.analytics.max-exact-values", 50_000);

    private final FileAnalytics analytics = new FileAnalytics();

    @Test
    void keepsExactFrequenciesForOrdinaryCategoricalColumns() {
        List<Map<String, Object>> rows = List.of(
                row("gender", "M"), row("gender", "F"), row("gender", "M"),
                row("gender", "M"), row("gender", "F")
        );

        ColumnProfile profile = profileSingleColumn("gender", rows.stream());

        assertEquals(2, profile.uniqueValues());
        assertEquals(List.of("M", "F"), profile.valueCounts().stream().map(Map.Entry::getKey).toList());
        assertEquals(List.of(3, 2), profile.valueCounts().stream().map(Map.Entry::getValue).toList());
    }

    @Test
    void boundsValueCountsForHighCardinalityColumns() {
        int rowCount = CAP * 2;
        Stream<Map<String, Object>> rows = IntStream.range(0, rowCount)
                .mapToObj(i -> row("identifier", "ID-" + i));

        ColumnProfile profile = profileSingleColumn("identifier", rows);

        assertTrue(profile.valueCounts().size() < CAP,
                "value counts must stay bounded, got " + profile.valueCounts().size());
        assertTrue(profile.uniqueValues() > rowCount * 0.9,
                "distinct estimate should stay close to the true cardinality, got " + profile.uniqueValues());
    }

    @Test
    void stillReportsQuantilesForHighCardinalityNumericColumns() {
        int rowCount = CAP * 2;
        Stream<Map<String, Object>> rows = IntStream.range(0, rowCount)
                .mapToObj(i -> row("measurement", String.valueOf(i)));

        ColumnProfile profile = profileSingleColumn("measurement", rows);

        assertEquals("INTEGER", profile.type());
        assertNotNull(profile.median(), "median must still be reported past the exact-tracking cap");
        assertTrue(Math.abs(profile.median() - rowCount / 2.0) < rowCount * 0.05,
                "median estimate out of tolerance: " + profile.median());
        assertEquals(0.0, profile.min());
        assertEquals(rowCount - 1.0, profile.max());
    }

    private ColumnProfile profileSingleColumn(String column, Stream<Map<String, Object>> rows) {
        FileProfile profile = analytics.profileRows(List.of(column), rows);
        assertNotNull(profile);
        assertEquals(1, profile.getColumns().size());
        return profile.getColumns().getFirst();
    }

    private static Map<String, Object> row(String column, Object value) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put(column, value);
        return row;
    }
}
