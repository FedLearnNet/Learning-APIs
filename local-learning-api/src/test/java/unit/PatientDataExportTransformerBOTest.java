package unit;

import bio.cosy.feddb.core.api.project.PatientDataPivotDuplicatePolicy;
import bio.cosy.feddb.core.api.project.PatientDataPivotJoinField;
import bio.cosy.feddb.local.api.cohort.patient.dataentry.PatientDataEntryExportDTO;
import bio.cosy.feddb.local.api.cohort.patient.export.PatientDataExportTransformerBO;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class PatientDataExportTransformerBOTest {

    private final PatientDataExportTransformerBO transformer = new PatientDataExportTransformerBO();

    @Test
    void longFormatUsesConfiguredJoinColumnsAndFeatureColumns() {
        PatientDataEntryExportDTO entry = dataEntry(1L, "feature_a", "42", "visit-1", "group-1");

        List<Map<String, Object>> rows = transformer.formatDataEntries(
                List.of(entry),
                false,
                EnumSet.of(PatientDataPivotJoinField.PATIENT_ID, PatientDataPivotJoinField.VISIT_ID),
                PatientDataPivotDuplicatePolicy.ERROR,
                List.of("feature_a")
        );

        assertEquals(List.of("patient_id", "visit_id", "feature_name", "feature_value"), rows.get(0).keySet().stream().toList());
        assertEquals(1L, rows.get(0).get("patient_id"));
        assertEquals("visit-1", rows.get(0).get("visit_id"));
        assertEquals("feature_a", rows.get(0).get("feature_name"));
        assertEquals("42", rows.get(0).get("feature_value"));
        assertFalse(rows.get(0).containsKey("patientId"));
        assertFalse(rows.get(0).containsKey("value"));
    }

    @Test
    void wideFormatUsesFeatureHeadersAndConfiguredGrouping() {
        List<PatientDataEntryExportDTO> entries = List.of(
                dataEntry(1L, "feature_a", "first-group", "visit-1", "group-1"),
                dataEntry(1L, "feature_a", "second-group", "visit-1", "group-2")
        );

        List<Map<String, Object>> rows = transformer.formatDataEntries(
                entries,
                true,
                EnumSet.of(PatientDataPivotJoinField.PATIENT_ID, PatientDataPivotJoinField.IMPORT_SCHEMA_GROUP_ID),
                PatientDataPivotDuplicatePolicy.ERROR,
                List.of("feature_a", "feature_b")
        );

        assertEquals(2, rows.size());
        assertEquals(List.of("patient_id", "import_schema_group_id", "feature_a", "feature_b"), rows.get(0).keySet().stream().toList());
        assertEquals("group-1", rows.get(0).get("import_schema_group_id"));
        assertEquals("first-group", rows.get(0).get("feature_a"));
        assertEquals("group-2", rows.get(1).get("import_schema_group_id"));
        assertEquals("second-group", rows.get(1).get("feature_a"));
    }

    @Test
    void wideFormatSpillsStrictDuplicatesIntoAdditionalRows() {
        List<PatientDataEntryExportDTO> entries = List.of(
                dataEntry(1L, "feature_a", "first", "visit-1", "group-1"),
                dataEntry(1L, "feature_b", "other-first", "visit-1", "group-1"),
                dataEntry(1L, "feature_a", "second", "visit-1", "group-1"),
                dataEntry(1L, "feature_b", "other-second", "visit-1", "group-1")
        );

        List<Map<String, Object>> rows = transformer.formatDataEntries(
                entries,
                true,
                EnumSet.of(PatientDataPivotJoinField.PATIENT_ID, PatientDataPivotJoinField.VISIT_ID),
                PatientDataPivotDuplicatePolicy.ERROR,
                List.of("feature_a", "feature_b")
        );

        assertEquals(2, rows.size());
        assertEquals("first", rows.get(0).get("feature_a"));
        assertEquals("other-first", rows.get(0).get("feature_b"));
        assertEquals("second", rows.get(1).get("feature_a"));
        assertEquals("other-second", rows.get(1).get("feature_b"));
        assertEquals(1L, rows.get(1).get("patient_id"));
        assertEquals("visit-1", rows.get(1).get("visit_id"));
    }

    @Test
    void wideFormatKeepsConfiguredDuplicatePolicies() {
        List<PatientDataEntryExportDTO> entries = List.of(
                dataEntry(1L, "feature_a", "first", "visit-1", "group-1"),
                dataEntry(1L, "feature_a", "second", "visit-1", "group-1")
        );

        List<Map<String, Object>> keepFirstRows = transformer.formatDataEntries(
                entries,
                true,
                EnumSet.of(PatientDataPivotJoinField.PATIENT_ID, PatientDataPivotJoinField.VISIT_ID),
                PatientDataPivotDuplicatePolicy.KEEP_FIRST,
                List.of("feature_a")
        );
        List<Map<String, Object>> keepLastRows = transformer.formatDataEntries(
                entries,
                true,
                EnumSet.of(PatientDataPivotJoinField.PATIENT_ID, PatientDataPivotJoinField.VISIT_ID),
                PatientDataPivotDuplicatePolicy.KEEP_LAST,
                List.of("feature_a")
        );

        assertEquals(1, keepFirstRows.size());
        assertEquals("first", keepFirstRows.get(0).get("feature_a"));
        assertEquals(1, keepLastRows.size());
        assertEquals("second", keepLastRows.get(0).get("feature_a"));
    }

    private PatientDataEntryExportDTO dataEntry(
            Long patientId,
            String name,
            Object value,
            String visitId,
            String importSchemaGroupId
    ) {
        PatientDataEntryExportDTO dto = new PatientDataEntryExportDTO();
        dto.setPatientId(patientId);
        dto.setName(name);
        dto.setOntologyId("ontology");
        dto.setDatatypeId("datatype");
        dto.setValue(value);
        dto.setVisitId(visitId);
        dto.setImportSchemaGroupId(importSchemaGroupId);
        return dto;
    }
}
