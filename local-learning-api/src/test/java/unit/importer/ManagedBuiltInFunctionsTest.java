package unit.importer;

import bio.cosy.feddb.local.api.importer.functions.managed.builtin.*;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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

    private static Map<String, Object> row(String key, Object value) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put(key, value);
        return map;
    }
}
