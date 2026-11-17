package bio.cosy.feddb.local.api.importer.functions.managed.builtin;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A list parameter given as an object says that an array is expected, so the next attempt can fix it. */
class ListParameterTest {

    @Test
    void readsAnArray() {
        assertEquals(List.of("a", "b"), BuiltInFunctionSupport.readStringList("[\"a\", \"b\"]", "parts"));
        assertEquals(List.of("a", "b"), BuiltInFunctionSupport.readStringList(List.of("a", "b"), "parts"));
    }

    @Test
    void saysAnArrayIsExpectedForAnObject() {
        for (Object parts : List.of(Map.of("first", "a"), "{\"first\": \"a\"}")) {
            IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                    () -> BuiltInFunctionSupport.readStringList(parts, "parts"));
            assertTrue(error.getMessage().contains("expected a JSON array"), error.getMessage());
        }
    }
}
