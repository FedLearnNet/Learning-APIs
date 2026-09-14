package unit.importer;

import bio.cosy.feddb.local.api.importer.functions.FunctionExecutionMode;
import bio.cosy.feddb.local.api.importer.functions.managed.builtin.ColumnSetIndicatorFunction;
import bio.cosy.feddb.local.api.importer.functions.managed.builtin.DateDifferenceFunction;
import bio.cosy.feddb.local.api.importer.functions.managed.builtin.FilterRowsByValueFunction;
import bio.cosy.feddb.local.api.importer.functions.managed.builtin.NumericRangeGroupFunction;
import bio.cosy.feddb.local.api.importer.functions.managed.builtin.NumericThresholdClassFunction;
import bio.cosy.feddb.local.api.importer.functions.managed.builtin.PatientDosageTrendFunction;
import bio.cosy.feddb.local.api.importer.functions.managed.builtin.PatientPriorEventCountFunction;
import bio.cosy.feddb.local.api.importer.functions.managed.builtin.PatientValueAggregateFunction;
import bio.cosy.feddb.local.api.importer.functions.managed.builtin.ReadmissionIntervalClassFunction;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Unit tests for the built-ins added to harmonize a relational clinical source
 * (MIMIC-IV) onto the shared diabetes schema. They run without a CDI container,
 * driving the functions through the same {@code apply(...)} contract the ETL
 * uses.
 */
class MimicHarmonizationFunctionsTest {

    // ------------------------------------------------------------------
    // Numeric Range Group Mapper
    // ------------------------------------------------------------------

    @Test
    void rangeGroupMapperBinsValuesIntoSchemaBands() {
        NumericRangeGroupFunction function = new NumericRangeGroupFunction();
        List<Map<String, Object>> rows = new ArrayList<>(List.of(
                row("age", "72"), row("age", "21"), row("age", "90"), row("age", ""), row("age", "n/a")));

        function.apply(rows, "age", Map.of(), Map.of(),
                Map.of("min", "0", "max", "100", "group_size", "10"), null, null, null);

        assertEquals("[70-80)", rows.get(0).get("age"));
        assertEquals("[20-30)", rows.get(1).get("age"));
        assertEquals("[90-100)", rows.get(2).get("age"));
        assertEquals("", rows.get(3).get("age"), "blank cells stay blank");
        assertEquals("n/a", rows.get(4).get("age"), "non-numeric cells are left to schema validation");
        assertEquals(FunctionExecutionMode.CELL, function.mode());
    }

    @Test
    void rangeGroupMapperLabelsValuesOutsideTheRange() {
        NumericRangeGroupFunction function = new NumericRangeGroupFunction();
        List<Map<String, Object>> rows = new ArrayList<>(List.of(
                row("weight", "310"), row("weight", "300"), row("weight", "-4")));

        function.apply(rows, "weight", Map.of(), Map.of(),
                Map.of("min", "0", "max", "300", "group_size", "25"), null, null, null);

        assertEquals(">300", rows.get(0).get("weight"));
        assertEquals(">300", rows.get(1).get("weight"), "the upper bound itself is outside the last band");
        assertEquals("<0", rows.get(2).get("weight"));
    }

    // ------------------------------------------------------------------
    // Numeric Threshold Class
    // ------------------------------------------------------------------

    @Test
    void thresholdClassMapsMeasurementsAndNotMeasuredSeparately() {
        NumericThresholdClassFunction function = new NumericThresholdClassFunction();
        List<Map<String, Object>> rows = new ArrayList<>(List.of(
                row("a1c", "6.2"), row("a1c", "7.4"), row("a1c", "9.1"), row("a1c", "")));
        rows.add(new LinkedHashMap<>()); // a row of another source table: no such column

        function.apply(rows, "a1c", Map.of(), Map.of(),
                Map.of("bins", "[{\"lt\":7,\"label\":\"Norm\"},{\"lt\":8,\"label\":\">7\"}]",
                        "else_label", ">8", "empty_label", "None"),
                null, null, null);

        assertEquals("Norm", rows.get(0).get("a1c"));
        assertEquals(">7", rows.get(1).get("a1c"));
        assertEquals(">8", rows.get(2).get("a1c"));
        assertEquals("None", rows.get(3).get("a1c"), "a present but empty measurement is 'not measured'");
        assertNull(rows.get(4).get("a1c"), "a row without the column keeps no value at all");
    }

    // ------------------------------------------------------------------
    // Date Difference
    // ------------------------------------------------------------------

    @Test
    void dateDifferenceDerivesLengthOfStayInDays() {
        DateDifferenceFunction function = new DateDifferenceFunction();
        List<Map<String, Object>> rows = new ArrayList<>(List.of(
                admission("2196-02-24 14:38:00", "2196-03-04 14:02:00"),
                admission("2196-02-24 14:38:00", "")));

        function.apply(rows, null,
                Map.of("start", "admittime", "end", "dischtime"),
                Map.of("value", "time_in_hospital"),
                Map.of("unit", "DAYS", "date_format", "yyyy-MM-dd HH:mm:ss", "decimals", 0),
                null, null, null);

        // 2196 is a leap year: 24 Feb 14:38 -> 4 Mar 14:02 is 8.975 days, rounded to 9.
        assertEquals("9", rows.get(0).get("time_in_hospital"));
        assertNull(rows.get(1).get("time_in_hospital"), "an incomplete stay yields no length");
    }

    // ------------------------------------------------------------------
    // Patient Value Aggregate
    // ------------------------------------------------------------------

    @Test
    void valueAggregateTakesThePerEncounterMaximumOntoTheEncounterRow() {
        PatientValueAggregateFunction function = new PatientValueAggregateFunction();
        List<Map<String, Object>> rows = new ArrayList<>(List.of(
                labEvent("1", "50852", "6.1"),
                labEvent("1", "50852", "7.9"),
                labEvent("1", "50931", "540"),
                labEvent("2", "50852", "12.0"),
                encounter("1"),
                encounter("2"),
                encounter("3")));

        function.apply(rows, null,
                Map.of("value", "valuenum", "key", "itemid", "group", "lab_hadm", "target_group", "hadm_id"),
                Map.of("value", "a1c"),
                Map.of("codes", List.of("50852"), "aggregate", "MAX", "write", "GROUP", "default", ""),
                null, null, null);

        assertEquals("7.9", rows.get(4).get("a1c"));
        assertEquals("12", rows.get(5).get("a1c"));
        assertEquals("", rows.get(6).get("a1c"), "an encounter without a matching lab is 'measured nothing'");
        assertNull(rows.get(0).get("a1c"), "source rows themselves stay untouched");
    }

    // ------------------------------------------------------------------
    // Patient Dosage Trend
    // ------------------------------------------------------------------

    @Test
    void dosageTrendComparesFirstAndLastDosePerEncounter() {
        PatientDosageTrendFunction function = new PatientDosageTrendFunction();
        List<Map<String, Object>> rows = new ArrayList<>(List.of(
                prescription("1", "MetFORMIN (Glucophage)", "500", "2196-02-25 10:00:00"),
                prescription("1", "MetFORMIN XR (Glucophage XR)", "1000", "2196-02-27 10:00:00"),
                prescription("2", "Insulin", "10", "2197-01-02 10:00:00"),
                encounter("1"),
                encounter("2")));

        function.apply(rows, null,
                Map.of("name", "drug", "dose", "dose_val_rx", "order", "starttime",
                        "group", "rx_hadm", "target_group", "hadm_id"),
                Map.of("value", "med_metformin"),
                Map.of("match", List.of("metformin", "glucophage"), "write", "GROUP", "default", "No"),
                null, null, null);

        assertEquals("Up", rows.get(3).get("med_metformin"));
        assertEquals("No", rows.get(4).get("med_metformin"), "an encounter without the agent is 'No'");
    }

    // ------------------------------------------------------------------
    // Column Set Indicator
    // ------------------------------------------------------------------

    @Test
    void columnSetIndicatorSummarisesTheMedicationColumns() {
        ColumnSetIndicatorFunction function = new ColumnSetIndicatorFunction();
        Map<String, Object> changed = medications("Steady", "Up");
        Map<String, Object> unchanged = medications("Steady", "No");
        Map<String, Object> none = medications("No", "No");
        Map<String, Object> otherTable = new LinkedHashMap<>();
        List<Map<String, Object>> rows = new ArrayList<>(List.of(changed, unchanged, none, otherTable));

        function.apply(rows, null,
                Map.of("m1", "med_metformin", "m2", "med_insulin"),
                Map.of("value", "change"),
                Map.of("values", List.of("Up", "Down"), "comparison", "IN", "mode", "ANY",
                        "match_label", "Ch", "default_label", "No"),
                null, null, null);

        function.apply(rows, null,
                Map.of("m1", "med_metformin", "m2", "med_insulin"),
                Map.of("value", "diabetes_med"),
                Map.of("values", List.of("No"), "comparison", "NOT_IN", "mode", "ANY",
                        "match_label", "Yes", "default_label", "No"),
                null, null, null);

        assertEquals("Ch", changed.get("change"));
        assertEquals("Yes", changed.get("diabetes_med"));
        assertEquals("No", unchanged.get("change"));
        assertEquals("Yes", unchanged.get("diabetes_med"));
        assertEquals("No", none.get("change"));
        assertEquals("No", none.get("diabetes_med"));
        assertNull(otherTable.get("change"), "rows without the medication columns stay untouched");
        assertNull(otherTable.get("diabetes_med"));
    }

    // ------------------------------------------------------------------
    // Readmission Interval Class
    // ------------------------------------------------------------------

    @Test
    void readmissionIntervalClassLabelsEachEncounter() {
        ReadmissionIntervalClassFunction function = new ReadmissionIntervalClassFunction();
        List<Map<String, Object>> rows = new ArrayList<>(List.of(
                admission("2196-01-01 08:00:00", "2196-01-05 08:00:00"),
                admission("2196-01-20 08:00:00", "2196-01-25 08:00:00"),
                admission("2196-06-01 08:00:00", "2196-06-03 08:00:00"),
                new LinkedHashMap<>()));

        function.apply(rows, null,
                Map.of("admission_date", "admittime", "discharge_date", "dischtime"),
                Map.of("value", "readmitted"),
                Map.of("days", 30, "date_format", "yyyy-MM-dd HH:mm:ss"),
                null, null, null);

        assertEquals("<30", rows.get(0).get("readmitted"));
        assertEquals(">30", rows.get(1).get("readmitted"));
        assertEquals("NO", rows.get(2).get("readmitted"));
        assertNull(rows.get(3).get("readmitted"), "rows that are not encounters get no outcome");
    }

    // ------------------------------------------------------------------
    // Filter Rows By Value
    // ------------------------------------------------------------------

    @Test
    void filterRowsKeepsTheConfiguredEventsAndEveryOtherTablesRows() {
        FilterRowsByValueFunction function = new FilterRowsByValueFunction();
        List<Map<String, Object>> rows = new ArrayList<>(List.of(
                labEvent("1", "50852", "6.1"),
                labEvent("1", "51301", "8.4"),
                labEvent("1", "50931", "140"),
                labEvent("2", "51222", "12.0"),
                encounter("1"),
                admission("2196-01-01 08:00:00", "2196-01-05 08:00:00")));

        List<Map<String, Object>> result = function.apply(
                rows, null,
                Map.of("value", "itemid"),
                Map.of(),
                Map.of("values", List.of("50852", "50931"), "mode", "KEEP", "on_missing", "KEEP"),
                null, null, null);

        assertEquals(4, result.size(), "two lab rows plus the rows of the other tables");
        assertEquals("50852", result.get(0).get("itemid"));
        assertEquals("50931", result.get(1).get("itemid"));
        assertEquals("1", result.get(2).get("hadm_id"), "the encounter row survives the lab filter");
        assertEquals("2196-01-01 08:00:00", result.get(3).get("admittime"));
    }

    @Test
    void filterRowsCanDropMatchesAndRowsWithoutTheColumn() {
        FilterRowsByValueFunction function = new FilterRowsByValueFunction();
        List<Map<String, Object>> rows = new ArrayList<>(List.of(
                labEvent("1", "50852", "6.1"),
                labEvent("1", "51301", "8.4"),
                encounter("1")));

        List<Map<String, Object>> result = function.apply(
                rows, null,
                Map.of("value", "itemid"),
                Map.of(),
                Map.of("values", List.of("51301"), "mode", "DROP", "on_missing", "DROP"),
                null, null, null);

        assertEquals(1, result.size());
        assertEquals("50852", result.getFirst().get("itemid"));
    }

    // ------------------------------------------------------------------
    // Patient Prior Event Count
    // ------------------------------------------------------------------

    @Test
    void priorEventCountCountsQualifyingAdmissionsInTheWindow() {
        PatientPriorEventCountFunction function = new PatientPriorEventCountFunction();
        List<Map<String, Object>> rows = new ArrayList<>(List.of(
                typedAdmission("2196-01-01 08:00:00", "EW EMER."),
                typedAdmission("2196-03-01 08:00:00", "ELECTIVE"),
                typedAdmission("2196-06-01 08:00:00", "EW EMER."),
                typedAdmission("2198-01-01 08:00:00", "EW EMER."),
                new LinkedHashMap<>()));

        function.apply(rows, null,
                Map.of("date", "admittime", "key", "admission_type"),
                Map.of("value", "number_emergency"),
                Map.of("codes", List.of("EW EMER."), "days", 365, "date_format", "yyyy-MM-dd HH:mm:ss"),
                null, null, null);

        assertEquals("0", rows.get(0).get("number_emergency"), "nothing precedes the first admission");
        assertEquals("1", rows.get(1).get("number_emergency"), "the elective stay still counts its predecessor");
        assertEquals("1", rows.get(2).get("number_emergency"), "only the emergency admission counts");
        assertEquals("0", rows.get(3).get("number_emergency"), "two years later the window is empty");
        assertNull(rows.get(4).get("number_emergency"), "rows that are not admissions get no count");
    }

    // ------------------------------------------------------------------
    // Fixtures
    // ------------------------------------------------------------------

    private static Map<String, Object> row(String column, Object value) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put(column, value);
        return row;
    }

    private static Map<String, Object> admission(String admittime, String dischtime) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("admittime", admittime);
        row.put("dischtime", dischtime);
        return row;
    }

    private static Map<String, Object> typedAdmission(String admittime, String admissionType) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("admittime", admittime);
        row.put("admission_type", admissionType);
        return row;
    }

    private static Map<String, Object> labEvent(String hadmId, String itemId, String value) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("lab_hadm", hadmId);
        row.put("itemid", itemId);
        row.put("valuenum", value);
        return row;
    }

    private static Map<String, Object> prescription(String hadmId, String drug, String dose, String start) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("rx_hadm", hadmId);
        row.put("drug", drug);
        row.put("dose_val_rx", dose);
        row.put("starttime", start);
        return row;
    }

    private static Map<String, Object> encounter(String hadmId) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("hadm_id", hadmId);
        return row;
    }

    private static Map<String, Object> medications(String metformin, String insulin) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("med_metformin", metformin);
        row.put("med_insulin", insulin);
        return row;
    }
}
