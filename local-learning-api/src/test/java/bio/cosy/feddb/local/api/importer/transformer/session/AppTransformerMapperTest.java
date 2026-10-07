package bio.cosy.feddb.local.api.importer.transformer.session;

import bio.cosy.feddb.core.api.run.RunStatusTypes;
import bio.cosy.feddb.core.api.run.StartRunDTO;
import bio.cosy.feddb.local.api.importer.run.step.ConnectorRunStepDTO;
import bio.cosy.feddb.local.api.importer.transformer.ConnectorTransformerDTO;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AppTransformerMapperTest {

    private final AppTransformerMapper mapper = new AppTransformerMapperImpl();

    @Test
    void appOutputKeepsTheColumnsTheAppDidNotReturn() {
        ConnectorTransformerDTO transformer = appTransformer();
        List<Map<String, Object>> sent = List.of(
                row("patient_nbr", "8222157", "diag_1", "250.83", "diag_2", "?"),
                row("patient_nbr", "55629189", "diag_1", "599", "diag_2", "276"));
        // What a combine app hands back: its own dataframe, which is not the row it was sent.
        List<Map<String, Object>> returned = List.of(
                Map.of("diag_combined", "250.83|?"),
                Map.of("diag_combined", "599|276"));

        List<Map<String, Object>> merged = mapper.merge(sent, returned, transformer);

        // Without this the patient id was gone after the first app step and every patient failed to
        // load with "Every mapped row must have an external patient id".
        assertEquals("8222157", merged.get(0).get("patient_nbr"));
        assertEquals("250.83", merged.get(0).get("diag_1"));
        assertEquals("250.83|?", merged.get(0).get("diag_combined"));
        assertEquals("599|276", merged.get(1).get("diag_combined"));
    }

    @Test
    void appOutputWinsOverTheColumnItRewrote() {
        List<Map<String, Object>> sent = List.of(row("patient_nbr", "8222157", "diag_1", "250.83", "diag_2", "?"));
        List<Map<String, Object>> returned = List.of(Map.of("diag_1", "250", "diag_2", "83"));

        List<Map<String, Object>> merged = mapper.merge(sent, returned, appTransformer());

        assertEquals("250", merged.get(0).get("diag_1"));
        assertEquals("83", merged.get(0).get("diag_2"));
        assertEquals("8222157", merged.get(0).get("patient_nbr"));
    }

    @Test
    void aDifferentNumberOfRowsIsTakenAsItIs() {
        List<Map<String, Object>> sent = List.of(
                row("patient_nbr", "8222157", "diag_1", "250.83", "diag_2", "?"),
                row("patient_nbr", "8222157", "diag_1", "599", "diag_2", "276"));
        // An app that filters or splits has changed the row set, so there is nothing to line up.
        List<Map<String, Object>> returned = List.of(Map.of("diag_combined", "250.83|?"));

        List<Map<String, Object>> merged = mapper.merge(sent, returned, appTransformer());

        assertEquals(1, merged.size());
        assertEquals(Map.of("diag_combined", "250.83|?"), merged.get(0));
    }

    @Test
    void stepCarriesTheTransformerAndItsColumnsAsHyperParams() {
        ConnectorTransformerDTO transformer = appTransformer();
        transformer.setId(5L);
        transformer.setColumn("diag_1");
        transformer.setHyperparams(Map.of("separator", "|"));

        ConnectorRunStepDTO step = mapper.toStep(transformer, 9L);

        assertNull(step.getId());
        assertEquals(9L, step.getConnectorRunId());
        assertEquals(5L, step.getTransformationId());
        assertEquals(RunStatusTypes.PENDING, step.getStatus());
        assertEquals(0f, step.getProgress());
        assertEquals(Map.of("column", "diag_1", "separator", "|"), step.getHyperParams());
    }

    @Test
    void batchSendsTheRowsInline() {
        List<Map<String, Object>> rows = List.of(row("patient_nbr", "8222157"));

        StartRunDTO batch = mapper.toBatch(-3L, appTransformer(), rows);

        assertEquals(-3L, batch.getId());
        assertEquals(RunStatusTypes.PENDING, batch.getStatus());
        assertEquals(rows, batch.getInputData().get(AppTransformerMapper.INPUT_VARIABLE));
        assertEquals(Map.of(), batch.getInputFilePaths());
    }

    private static ConnectorTransformerDTO appTransformer() {
        ConnectorTransformerDTO transformer = new ConnectorTransformerDTO();
        transformer.setAppImage("transformer-combine-rows:qfyjTIKg");
        return transformer;
    }

    private static Map<String, Object> row(Object... keysAndValues) {
        Map<String, Object> row = new LinkedHashMap<>();
        for (int index = 0; index < keysAndValues.length; index += 2) {
            row.put(String.valueOf(keysAndValues[index]), keysAndValues[index + 1]);
        }
        return row;
    }
}
