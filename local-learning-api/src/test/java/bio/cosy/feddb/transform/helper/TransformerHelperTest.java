package bio.cosy.feddb.transform.helper;

import bio.cosy.feddb.core.api.file.analytics.ColumnProfile;
import bio.cosy.feddb.local.agent.etl.transform.assistant.TransformerAssistantMessageDTO;
import bio.cosy.feddb.local.agent.etl.transform.assistant.TransformerAssistantMessageType;
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
