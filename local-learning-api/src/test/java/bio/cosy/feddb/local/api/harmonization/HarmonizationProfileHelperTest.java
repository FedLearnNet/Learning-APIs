package bio.cosy.feddb.local.api.harmonization;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
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

    @SafeVarargs
    private static String pattern(Map.Entry<String, Integer>... values) {
        return HarmonizationProfileHelper.pattern(List.of(values)).orElseThrow().getValidator();
    }
}
