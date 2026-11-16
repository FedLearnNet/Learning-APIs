package bio.cosy.feddb.local.api.importer.functions.managed.builtin;

import bio.cosy.feddb.local.api.importer.functions.managed.CellFunctionExecutionContext;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** 'null' as replacement is the empty value in every built-in, like in Replace Value. */
class NullReplacementTest {

    @Test
    void normalizeMissingTurnsATokenEmpty() {
        assertEquals("", new NormalizeMissingFunction().execute(context("NA", Map.of("tokens", "[\"NA\"]", "replacement", "null"))));
        assertEquals("", new NormalizeMissingFunction().execute(context(null, Map.of("tokens", "[\"NA\"]", "replacement", "null"))));
    }

    @Test
    void regexReplaceTurnsAMatchingValueEmpty() {
        RegexReplaceFunction regexReplace = new RegexReplaceFunction();
        assertNull(regexReplace.execute(context("250.01", Map.of("pattern", "^25", "replacement", "null"))));
        assertEquals("401", regexReplace.execute(context("401", Map.of("pattern", "^25", "replacement", "null"))));
        assertEquals("250", regexReplace.execute(context("250.01", Map.of("pattern", "\\..*", "replacement", ""))));
    }

    private static CellFunctionExecutionContext context(Object value, Map<String, Object> params) {
        CellFunctionExecutionContext context = new CellFunctionExecutionContext();
        context.setValue(value);
        context.setParams(params);
        return context;
    }
}
