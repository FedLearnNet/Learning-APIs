package bio.cosy.feddb.transform.helper;

import bio.cosy.feddb.local.agent.etl.transform.assistant.TransformerAssistantMessageDTO;
import bio.cosy.feddb.local.agent.etl.transform.assistant.TransformerAssistantMessageType;
import bio.cosy.feddb.local.agent.etl.transform.helper.TransformerHelper;
import bio.cosy.feddb.local.agent.etl.transform.model.ConnectorSelectedTransformer;
import bio.cosy.feddb.local.agent.etl.transform.model.VisitMapping;
import bio.cosy.feddb.local.api.importer.files.table.TableSample;
import bio.cosy.feddb.local.api.importer.mapping.ConnectorMappingDTO;
import org.junit.jupiter.api.Test;

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

        assertTrue(TransformerHelper.hasSeveralRowsPerPatient(ENCOUNTERS, "patient_nbr"));
        assertFalse(TransformerHelper.hasSeveralRowsPerPatient(onePerPatient, "patient_nbr"));
        assertFalse(TransformerHelper.hasSeveralRowsPerPatient(ENCOUNTERS, null));
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
}
