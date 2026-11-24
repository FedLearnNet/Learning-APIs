package bio.cosy.feddb.transform.helper;

import bio.cosy.feddb.core.api.file.analytics.ColumnProfile;
import bio.cosy.feddb.local.agent.etl.transform.assistant.TransformerAssistantMessageDTO;
import bio.cosy.feddb.local.agent.etl.transform.assistant.TransformerAssistantMessageType;
import bio.cosy.feddb.local.agent.etl.transform.helper.PackedValues;
import bio.cosy.feddb.local.agent.etl.transform.helper.TransformerHelper;
import bio.cosy.feddb.local.agent.etl.transform.model.ConnectorSelectedTransformer;
import bio.cosy.feddb.local.agent.etl.transform.model.VisitMapping;
import bio.cosy.feddb.local.api.importer.files.table.TableSample;
import bio.cosy.feddb.local.api.importer.mapping.ConnectorMappingDTO;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TransformerHelperTest {

    private static final TableSample ENCOUNTERS = new TableSample(List.of("patient_nbr", "encounter_id", "admitted", "age"),
            List.of(Map.of("patient_nbr", "1", "encounter_id", "10", "admitted", "03.01.2020", "age", "40"),
                    Map.of("patient_nbr", "1", "encounter_id", "11", "admitted", "17.05.2021", "age", "41"),
                    Map.of("patient_nbr", "2", "encounter_id", "12", "admitted", "28.02.2020", "age", "63")),
            null);

    @Test
    void severalRowsOfOnePatientNeedAVisit() {
        TableSample onePerPatient = new TableSample(List.of("patient_nbr"),
                List.of(Map.of("patient_nbr", "1"), Map.of("patient_nbr", "2")), null);

        assertTrue(TransformerHelper.hasSeveralRowsPerPatient(ENCOUNTERS, "patient_nbr", Map.of()));
        assertFalse(TransformerHelper.hasSeveralRowsPerPatient(onePerPatient, "patient_nbr", Map.of()));
        assertFalse(TransformerHelper.hasSeveralRowsPerPatient(ENCOUNTERS, null, Map.of()));
    }

    @Test
    void theWholeFileDecidesWhetherPatientsHaveSeveralRows() {
        TableSample onePerPatient = new TableSample(List.of("patient_nbr"),
                List.of(Map.of("patient_nbr", "1"), Map.of("patient_nbr", "2")), null);
        ColumnProfile fourPatientsInSixRows = profile("patient_nbr", 6, 0, 4, List.of());

        assertTrue(TransformerHelper.hasSeveralRowsPerPatient(onePerPatient, "patient_nbr",
                Map.of("patient_nbr", fourPatientsInSixRows)));
    }

    @Test
    void valueRowsHoldEveryValueOfTheFile() {
        ColumnProfile codes = profile("age", 783, 2, 3, List.of(Map.entry("0", 750), Map.entry("2", 24), Map.entry("1", 7)));

        List<Map<String, Object>> rows = TransformerHelper.valueRows(ENCOUNTERS, List.of("age"), Map.of("age", codes), "patient_nbr");

        assertEquals(Arrays.asList("0", "2", "1", null), rows.stream().map(row -> row.get("age")).toList());
        assertEquals(List.of("value-row-0", "value-row-1", "value-row-2", "value-row-3"),
                rows.stream().map(row -> row.get("patient_nbr")).toList());
        assertEquals("10", rows.getFirst().get("encounter_id"));
    }

    @Test
    void fileValuesAreDescribedWithTheirCounts() {
        ColumnProfile codes = profile("repaglinide_rx", 783, 0, 3, List.of(Map.entry("0", 750), Map.entry("2", 24), Map.entry("1", 9)));

        assertEquals("repaglinide_rx (783 rows, 0 empty): '0' x750, '2' x24, '1' x9",
                TransformerHelper.describeFileValues(List.of("repaglinide_rx", "unknown"), Map.of("repaglinide_rx", codes)));
    }

    @Test
    void packedCodesAreNamedInTheDescription() {
        ColumnProfile codes = profile("EncounterCodes", 783, 0, 100,
                List.of(Map.entry("1-1-7", 218), Map.entry("3-1-1", 68), Map.entry("1-18-7", 31)));

        assertTrue(TransformerHelper.describeFileValues(codes, 30)
                .endsWith(" Packed: each cell joins 3 values by '-', e.g. '1-1-7'; the column can hold several schema nodes, Split Column splits it."));
        assertTrue(PackedValues.note(List.of("786|250|401", "414|401|250", "250.8|707|682")).contains("3 values by '|'"));
    }

    @Test
    void datesIdsRangesAndListsAreNotPacked() {
        assertEquals("", PackedValues.note(List.of("2014-03-20", "1982-10-25", "2020-01-01 10:00")));
        assertEquals("", PackedValues.note(List.of("b0a06ead-cc42-aa48-dad6-841d4aa679fa", "5ff84b1d-2c3e-4f3e-a0b5-16f1a62f2d29")));
        assertEquals("", PackedValues.note(List.of("70-79", "60-69", "-99")));
        assertEquals("", PackedValues.note(List.of("a,b", "a,b,c,d", "c")));
        assertEquals("", PackedValues.note(List.of("1-1-7")));
        assertEquals("", PackedValues.note(List.of("999-76-6866", "999-12-3456", "999-40-1234")));
        assertEquals("", PackedValues.note(List.of("C3L-00006-01", "C3N-01234-02", "C3L-00451-01")));
    }

    @Test
    void validVisitMappingIsKept() {
        VisitMapping visit = new VisitMapping("encounter_id", "admitted", "dd.MM.yyyy");

        assertEquals(visit, TransformerHelper.checkVisitMapping(visit, ENCOUNTERS, "patient_nbr"));
    }

    @Test
    void formatThatDoesNotParseTheValuesIsDropped() {
        VisitMapping checked = TransformerHelper.checkVisitMapping(
                new VisitMapping("encounter_id", "admitted", "yyyy-MM-dd"), ENCOUNTERS, "patient_nbr");

        assertEquals(new VisitMapping("encounter_id", "admitted", null), checked);
    }

    @Test
    void patientIdAndUnknownColumnsAreNoVisit() {
        assertNull(TransformerHelper.checkVisitMapping(
                new VisitMapping("patient_nbr", "admission_date", null), ENCOUNTERS, "patient_nbr"));
    }

    @Test
    void visitMappingIsCopiedOntoTheSchemaMapping() {
        TransformerAssistantMessageDTO result = TransformerAssistantMessageDTO.node(TransformerAssistantMessageType.NODE_RESULT,
                7L, "Demographics.Age", ConnectorSelectedTransformer.mapping("age", List.of()));
        result.setVisitMapping(new VisitMapping("encounter_id", "admitted", "dd.MM.yyyy"));

        ConnectorMappingDTO mapping = TransformerHelper.toConnector(List.of(result)).getSchemaMapping().getFirst();

        assertEquals("encounter_id", mapping.getVisitIdMapping());
        assertEquals("admitted", mapping.getVisitTimestampMapping());
        assertEquals("dd.MM.yyyy", mapping.getTimestampFormat());
    }

    private static ColumnProfile profile(String name, long count, long missing, int unique,
                                         List<Map.Entry<String, Integer>> valueCounts) {
        return new ColumnProfile(name, "TEXT", count, missing, unique, null, null, null, null, null, null, null,
                null, valueCounts);
    }
}
