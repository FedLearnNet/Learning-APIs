package unit.importer;

import bio.cosy.feddb.local.api.importer.functions.FunctionExecutionMode;
import bio.cosy.feddb.local.api.importer.functions.FunctionParameterUsage;
import bio.cosy.feddb.local.api.importer.functions.managed.builtin.FilterPatientByHitFunction;
import bio.cosy.feddb.local.api.importer.functions.managed.builtin.ReadmissionTargetFunction;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class ManagedPatientFunctionsTest {

    @Test
    void filterKeepsEveryRowWhenOnePatientRowContainsTheHit() {
        FilterPatientByHitFunction function = new FilterPatientByHitFunction();
        List<Map<String, Object>> rows = new ArrayList<>(List.of(
                row("diagnosis", "control"),
                row("diagnosis", "HIT"),
                row("diagnosis", "follow-up")
        ));

        List<Map<String, Object>> result = function.apply(
                rows,
                null,
                Map.of("value", "diagnosis"),
                Map.of(),
                Map.of("hit", "hit"),
                null,
                null,
                null
        );

        assertEquals(FunctionExecutionMode.PATIENT, function.mode());
        assertEquals(FunctionParameterUsage.INPUT, function.parameters().getFirst().getUsage());
        assertEquals(FunctionParameterUsage.HYPERPARAMETER, function.parameters().get(1).getUsage());
        assertSame(rows, result);
        assertEquals(3, result.size());
    }

    @Test
    void filterRemovesEveryRowWhenPatientHasNoHit() {
        FilterPatientByHitFunction function = new FilterPatientByHitFunction();

        List<Map<String, Object>> result = function.apply(
                new ArrayList<>(List.of(row("diagnosis", "control"), row("diagnosis", "follow-up"))),
                null,
                Map.of("value", "diagnosis"),
                Map.of(),
                Map.of("hit", "hit"),
                null,
                null,
                null
        );

        assertEquals(List.of(), result);
    }

    @Test
    void readmissionTargetUsesNextNonEmptyDateInChronologicalOrder() {
        ReadmissionTargetFunction function = new ReadmissionTargetFunction();
        List<Map<String, Object>> rows = new ArrayList<>(List.of(
                row("encounter_date", "2024-03-01"),
                row("encounter_date", "2024-01-01"),
                row("encounter_date", ""),
                row("encounter_date", "2024-01-20")
        ));

        List<Map<String, Object>> result = function.apply(
                rows,
                null,
                Map.of("date", "encounter_date"),
                Map.of("target", "readmitted"),
                Map.of("days", 30, "date_format", "%Y-%m-%d"),
                null,
                null,
                null
        );

        assertEquals("2024-01-01", result.get(0).get("encounter_date"));
        assertEquals(1, result.get(0).get("readmitted"));
        assertEquals("2024-01-20", result.get(1).get("encounter_date"));
        assertEquals(0, result.get(1).get("readmitted"));
        assertEquals("2024-03-01", result.get(2).get("encounter_date"));
        assertEquals(0, result.get(2).get("readmitted"));
        assertEquals("", result.get(3).get("encounter_date"));
        assertEquals(0, result.get(3).get("readmitted"));
    }

    @Test
    void readmissionTargetSupportsCustomDateFormatAndInclusiveThreshold() {
        ReadmissionTargetFunction function = new ReadmissionTargetFunction();
        List<Map<String, Object>> rows = new ArrayList<>(List.of(
                row("encounter_date", "01.01.2024"),
                row("encounter_date", "31.01.2024")
        ));

        List<Map<String, Object>> result = function.apply(
                rows,
                null,
                Map.of("date", "encounter_date"),
                Map.of("target", "readmitted"),
                Map.of("days", "30", "date_format", "%d.%m.%Y"),
                null,
                null,
                null
        );

        assertEquals(1, result.get(0).get("readmitted"));
        assertEquals(0, result.get(1).get("readmitted"));
    }

    private static Map<String, Object> row(String key, Object value) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put(key, value);
        return row;
    }
}
