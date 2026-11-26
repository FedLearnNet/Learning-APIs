package unit.importer;

import bio.cosy.feddb.local.api.importer.functions.managed.builtin.*;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ManagedBuiltInFunctionsTest {

    @Test
    void dateTimeToDateSupportsPythonStyleFormats() {
        DateTimeToDateFunction function = new DateTimeToDateFunction();

        List<Map<String, Object>> rows = List.of(row("value", "2024-05-17"));
        List<Map<String, Object>> result = function.apply(
                rows,
                "value",
                (Map<String, String>) null,
                (Map<String, String>) null,
                Map.of("datetime_format", "'%Y-%m-%d'"),
                (String) null,
                (Long) null,
                (Long) null
        );

        assertEquals("2024-05-17", result.get(0).get("value"));
    }

    @Test
    void reformatDateReducesDateToYear() {
        assertEquals("1975", reformatDate("1975-06-23", "%Y-%m-%d", "%Y"));
    }

    @Test
    void reformatDateConvertsLocalNotationToIso() {
        assertEquals("1975-06-23", reformatDate("23.06.1975", "%d.%m.%Y", "%Y-%m-%d"));
    }

    @Test
    void reformatDateReformatsDateTimes() {
        assertEquals("17.05.2024 13:45", reformatDate("2024-05-17 13:45:00", "%Y-%m-%d %H:%M:%S", "%d.%m.%Y %H:%M"));
        assertEquals("2024-05-17T13:45", reformatDate("2024-05-17 13:45:00", "%Y-%m-%d %H:%M:%S", "%Y-%m-%d'T'%H:%M"));
    }

    @Test
    void reformatDateFallsBackToIsoDateTimeInput() {
        assertEquals("1975", reformatDate("1975-06-23T08:30:00", "%d.%m.%Y", "%Y"));
    }

    @Test
    void reformatDateLeavesUnreadableAndBlankValuesUnchanged() {
        assertEquals("unknown", reformatDate("unknown", "%Y-%m-%d", "%Y"));
        assertEquals("1975-06-23", reformatDate("1975-06-23", "%Y-%m-%d", "%Y-%m-%dT%H"));
        assertEquals("", reformatDate("", "%Y-%m-%d", "%Y"));
        assertNull(reformatDate(null, "%Y-%m-%d", "%Y"));
    }

    @Test
    void snomedGenderMapperMapsNumericValues() {
        SnomedGenderMapperFunction function = new SnomedGenderMapperFunction();

        List<Map<String, Object>> result = function.apply(
                List.of(row("gender", 248152002.0)),
                "gender",
                (Map<String, String>) null,
                (Map<String, String>) null,
                (Map<String, Object>) null,
                (String) null,
                (Long) null,
                (Long) null
        );

        assertEquals("Female (finding)", result.get(0).get("gender"));
    }

    @Test
    void minorityStatusMapperMapsCodes() {
        MinorityStatusMapperFunction function = new MinorityStatusMapperFunction();

        List<Map<String, Object>> result = function.apply(
                List.of(row("value", "3")),
                "value",
                (Map<String, String>) null,
                (Map<String, String>) null,
                (Map<String, Object>) null,
                (String) null,
                (Long) null,
                (Long) null
        );

        assertEquals("Asian", result.get(0).get("value"));
    }

    @Test
    void employmentMapperMapsCodes() {
        EmploymentMapperFunction function = new EmploymentMapperFunction();

        List<Map<String, Object>> result = function.apply(
                List.of(row("value", "5")),
                "value",
                (Map<String, String>) null,
                (Map<String, String>) null,
                (Map<String, Object>) null,
                (String) null,
                (Long) null,
                (Long) null
        );

        assertEquals("Student", result.get(0).get("value"));
    }

    @Test
    void maritalStatusMapperMapsCodes() {
        MaritalStatusMapperFunction function = new MaritalStatusMapperFunction();

        List<Map<String, Object>> result = function.apply(
                List.of(row("value", "2")),
                "value",
                (Map<String, String>) null,
                (Map<String, String>) null,
                (Map<String, Object>) null,
                (String) null,
                (Long) null,
                (Long) null
        );

        assertEquals("Married", result.get(0).get("value"));
    }

    @Test
    void educationMapperMapsFloatingWholeNumbers() {
        EducationMapperFunction function = new EducationMapperFunction();

        List<Map<String, Object>> result = function.apply(
                List.of(row("value", 4.0)),
                "value",
                (Map<String, String>) null,
                (Map<String, String>) null,
                (Map<String, Object>) null,
                (String) null,
                (Long) null,
                (Long) null
        );

        assertEquals("University education", result.get(0).get("value"));
    }

    @Test
    void removeColumnsDropsTheNamedColumnsFromEveryRow() {
        RemoveColumnsFunction function = new RemoveColumnsFunction();
        List<Map<String, Object>> rows = new ArrayList<>(List.of(
                diagnosisRow("250.83", "?", "250.83|?"),
                diagnosisRow("599", "276", "599|276")));

        function.apply(rows, "diag_1, diag_2", null, null, null, null, null, null);

        // The columns a combine step already folded into diag_combined are gone from the rows
        // themselves, which is what takes them out of the preview table and the mapping.
        assertEquals(List.of("diag_combined"), List.copyOf(rows.get(0).keySet()));
        assertEquals("599|276", rows.get(1).get("diag_combined"));
    }

    @Test
    void removeColumnsIgnoresAColumnThatIsNotThere() {
        RemoveColumnsFunction function = new RemoveColumnsFunction();
        List<Map<String, Object>> rows = new ArrayList<>(List.of(
                diagnosisRow("250.83", "?", "250.83|?")));

        function.apply(rows, "diag_1,diag_9", null, null, null, null, null, null);

        assertEquals(List.of("diag_2", "diag_combined"), List.copyOf(rows.get(0).keySet()));
    }

    @Test
    void removeColumnsRefusesAnEmptySelection() {
        RemoveColumnsFunction function = new RemoveColumnsFunction();
        List<Map<String, Object>> rows = new ArrayList<>(List.of(diagnosisRow("250.83", "?", "250.83|?")));

        assertThrows(IllegalArgumentException.class,
                () -> function.apply(rows, "  ", null, null, null, null, null, null));
    }

    private static Map<String, Object> diagnosisRow(Object diag1, Object diag2, Object combined) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("diag_1", diag1);
        map.put("diag_2", diag2);
        map.put("diag_combined", combined);
        return map;
    }

    @Test
    void wideToLongAddsOneRowPerFilledMeasurement() {
        List<Map<String, Object>> rows = new ArrayList<>(List.of(labRow("6.1", "%", "", "mg/dL", "24.3")));

        List<Map<String, Object>> result = new WideToLongFunction().apply(rows, null,
                Map.of("patient id", "pid", "visit id", "visit", "start date", "date"),
                Map.of("variable name", "lab name", "variable value", "lab value"),
                Map.of("measurement columns", "{\"HbA1c\": \"HbA1c unit\", \"Glucose\": \"Glucose unit\", \"BMI\": null}"),
                null, null, null);

        assertEquals(3, result.size());
        // The original row keeps its other data once, without the measurement columns
        Map<String, Object> original = result.get(0);
        assertEquals("female", original.get("sex"));
        assertEquals("2024-05-17", original.get("date"));
        assertEquals("1-12", original.get("HbA1c range"));
        assertFalse(original.containsKey("HbA1c"));
        assertFalse(original.containsKey("HbA1c unit"));
        assertNull(original.get("lab name"));

        Map<String, Object> hba1c = result.get(1);
        assertNull(hba1c.get("sex"));
        assertNull(hba1c.get("date"));
        assertEquals("p1", hba1c.get("pid"));
        assertEquals("v1", hba1c.get("visit"));
        assertEquals("HbA1c", hba1c.get("lab name"));
        assertEquals("6.1", hba1c.get("lab value"));
        assertEquals("%", hba1c.get("variable unit"));
        assertEquals("2024-05-17", hba1c.get("start date"));
        assertNull(hba1c.get("end date"));

        // Glucose is empty, so the next row is BMI, which has no unit column
        Map<String, Object> bmi = result.get(2);
        assertEquals("BMI", bmi.get("lab name"));
        assertEquals("24.3", bmi.get("lab value"));
        assertNull(bmi.get("variable unit"));
    }

    @Test
    void wideToLongKeepsARowWithoutMeasurements() {
        List<Map<String, Object>> rows = new ArrayList<>(List.of(labRow("", "%", "", "", null)));

        List<Map<String, Object>> result = new WideToLongFunction().apply(rows, null, Map.of("patient id", "pid"),
                null, Map.of("measurement columns", Map.of("HbA1c", "HbA1c unit")), null, null, null);

        assertEquals(1, result.size());
        assertEquals("female", result.get(0).get("sex"));
        assertNull(result.get(0).get("variable name"));
        assertNull(result.get(0).get("start date"));
    }

    @Test
    void wideToLongExplainsWrongSettings() {
        WideToLongFunction function = new WideToLongFunction();
        List<Map<String, Object>> rows = new ArrayList<>(List.of(labRow("6.1", "%", "", "", null)));
        Map<String, String> inputs = Map.of("patient id", "pid");

        for (Object columns : List.of("{}", "[\"HbA1c\"]", List.of("HbA1c"), "not json")) {
            IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                    () -> function.apply(rows, null, inputs, null, Map.of("measurement columns", columns), null, null, null));
            assertTrue(error.getMessage().contains("\"HbA1c\": \"HbA1c unit\""), error.getMessage());
        }
        IllegalArgumentException noPatient = assertThrows(IllegalArgumentException.class,
                () -> function.apply(rows, null, Map.of(), null,
                        Map.of("measurement columns", "{\"HbA1c\": null}"), null, null, null));
        assertTrue(noPatient.getMessage().contains("patient ID"), noPatient.getMessage());
    }

    private static Map<String, Object> labRow(Object hba1c, Object hba1cUnit, Object glucose, Object glucoseUnit,
                                              Object bmi) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("pid", "p1");
        map.put("visit", "v1");
        map.put("date", "2024-05-17");
        map.put("sex", "female");
        map.put("HbA1c", hba1c);
        map.put("HbA1c unit", hba1cUnit);
        map.put("HbA1c range", "1-12");
        map.put("Glucose", glucose);
        map.put("Glucose unit", glucoseUnit);
        map.put("BMI", bmi);
        return map;
    }

    private static Object reformatDate(Object value, String inputFormat, String outputFormat) {
        List<Map<String, Object>> result = new ReformatDateFunction().apply(
                List.of(row("value", value)),
                "value",
                (Map<String, String>) null,
                (Map<String, String>) null,
                Map.of("input_format", inputFormat, "output_format", outputFormat),
                (String) null,
                (Long) null,
                (Long) null
        );
        return result.get(0).get("value");
    }

    private static Map<String, Object> row(String key, Object value) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put(key, value);
        return map;
    }
}
