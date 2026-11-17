package bio.cosy.feddb.local.api.harmonization;

import bio.cosy.feddb.core.api.file.analytics.ColumnProfile;
import bio.cosy.feddb.local.api.schema.datatype.DataTypeDTO;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HarmonizationProfileHelperTest {

    @Test
    void describesTheShapesOfTheReferenceValues() {
        String pattern = pattern(Map.entry("428", 10), Map.entry("V27", 3), Map.entry("250.01", 2));

        assertEquals("(?:\\d+|[A-Za-z]+\\d+|\\d+\\.\\d+)", pattern);
        assertTrue(Pattern.matches(pattern, "E849"));
        assertFalse(Pattern.matches(pattern, "786|250|401"));
    }

    @Test
    void usesOnlyRegexSyntaxPythonKnowsToo() {
        String pattern = pattern(Map.entry("Größe", 1), Map.entry("V27", 1));

        assertFalse(pattern.contains("\\p") || pattern.contains("\\Q"), pattern);
        assertTrue(Pattern.matches(pattern, "Größe"));
    }

    @Test
    void allowsTheMissingValueMarkerOfTheReference() {
        List<Map.Entry<String, Integer>> values = List.of(Map.entry("Norm", 20), Map.entry(">7", 10));
        ColumnProfile profile = new ColumnProfile("A1Cresult", "TEXT", 100, 70, 2, null, null, null, null, null, null, null,
                values, values, List.of(Map.entry("None", 60)));

        DataTypeDTO dataType = HarmonizationProfileHelper.dataType(profile);

        assertEquals(Set.of("Norm", ">7", "None"), Set.copyOf(dataType.getAllowedValues()));
        assertTrue(dataType.getDescription().contains("None 60%") && dataType.getDescription().contains("(empty) 10%"),
                dataType.getDescription());
    }

    @SafeVarargs
    private static String pattern(Map.Entry<String, Integer>... values) {
        return HarmonizationProfileHelper.pattern(List.of(values)).orElseThrow().getValidator();
    }
}
