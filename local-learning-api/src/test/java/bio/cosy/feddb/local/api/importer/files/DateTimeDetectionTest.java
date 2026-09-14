package bio.cosy.feddb.local.api.importer.files;

import bio.cosy.feddb.core.api.file.analytics.ColumnProfile;
import bio.cosy.feddb.core.api.file.analytics.RowProfiler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Timestamp columns are recognised by a hand-rolled ISO check before any {@code DateTimeFormatter}
 * is involved, because the formatter path cost roughly ten times what any other column type did.
 * These pin that the shortcut agrees with the formatters in both directions: everything the
 * formatters accept is still DATETIME, and nothing new slips into that bucket.
 */
class DateTimeDetectionTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "2024-01-02",
            "2024-01-02T08:30",
            "2024-01-02T08:30:15",
            "2024-01-02T08:30:15.123",
            "2024-01-02T08:30:15.123456789",
            "2024-01-02T08:30:15Z",
            "2024-01-02T08:30:15+02:00",
            "2024-01-02T08:30:15.500-05:30",
            "2024-02-29",          // leap day
            "2000-02-29",          // century leap day
            "1999-12-31T23:59:59",
    })
    void recognisesIsoTimestamps(String value) {
        assertEquals("DATETIME", typeOf(value));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "2023-02-29",          // not a leap year
            "2024-13-01",          // month out of range
            "2024-00-10",          // month out of range
            "2024-01-32",          // day out of range
            "2024-04-31",          // April has 30 days
            "2024-01-02 08:30:15", // space separator is not ISO
            "2024-01-02T25:00:00", // hour out of range
            "2024-01-02T08:61:00", // minute out of range
            "02/01/2024",
            "not a date",
    })
    void leavesNonIsoValuesOutOfTheDatetimeBucket(String value) {
        assertEquals("TEXT", typeOf(value));
    }

    /** Forms only the formatters know; they must keep working through the fallback. */
    @ParameterizedTest
    @ValueSource(strings = {
            "08:30:15",                        // ISO_LOCAL_TIME
            "2024-01-02T08:30:15+01:00[Europe/Berlin]", // ISO_ZONED_DATE_TIME
    })
    void stillFallsBackToTheFormattersForTheRest(String value) {
        assertEquals("DATETIME", typeOf(value));
    }

    @Test
    void treatsPurelyNumericValuesAsNumbersNotTimestamps() {
        assertEquals("INTEGER", typeOf("20240102"));
        assertEquals("INTEGER", typeOf("2024"));
    }

    private static String typeOf(String value) {
        RowProfiler profiler = RowProfiler.of(List.of("column"));
        for (int i = 0; i < 5; i++) {
            profiler.startRow();
            profiler.accept(0, value);
            profiler.endRow();
        }
        List<ColumnProfile> columns = profiler.finish().getColumns();
        return columns.getFirst().type();
    }
}
