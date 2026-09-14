package unit;

import bio.cosy.feddb.core.api.file.analytics.ColumnProfile;
import bio.cosy.feddb.core.api.file.analytics.FileAnalytics;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FileAnalyticsValueCountsTest {

    @Test
    void profileExposesEveryDistinctValueAndKeepsTopCategoriesBounded() {
        String rows = """
                [
                  {"category":"A"},
                  {"category":"B"},
                  {"category":"A"},
                  {"category":"C"}
                ]
                """;

        ColumnProfile profile = new FileAnalytics().profileJson(rows).getColumns().getFirst();

        assertEquals(3, profile.uniqueValues());
        assertEquals(List.of("A", "B", "C"), profile.valueCounts().stream()
                .map(entry -> entry.getKey())
                .toList());
        assertEquals(List.of(2, 1, 1), profile.valueCounts().stream()
                .map(entry -> entry.getValue())
                .toList());
        assertEquals(profile.valueCounts(), profile.topCategories());
    }

    @Test
    void streamingProfileMatchesJsonProfileWithoutMaterializingTheTable() {
        FileAnalytics analytics = new FileAnalytics();

        ColumnProfile profile = analytics.profileRows(
                List.of("category"),
                List.of(
                        Map.<String, Object>of("category", "A"),
                        Map.<String, Object>of("category", "B"),
                        Map.<String, Object>of("category", "A"),
                        Map.<String, Object>of("category", "C")
                ).stream()
        ).getColumns().getFirst();

        assertEquals(4, profile.count());
        assertEquals(3, profile.uniqueValues());
        assertEquals(List.of("A", "B", "C"), profile.valueCounts().stream()
                .map(Map.Entry::getKey)
                .toList());
    }
}
