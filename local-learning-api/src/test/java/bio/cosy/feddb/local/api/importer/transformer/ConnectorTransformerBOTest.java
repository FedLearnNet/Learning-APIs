package bio.cosy.feddb.local.api.importer.transformer;

import bio.cosy.feddb.local.api.importer.functions.FunctionRunnerBO;
import bio.cosy.feddb.local.api.importer.functions.managed.builtin.FilterPatientByHitFunction;
import jakarta.ws.rs.BadRequestException;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ConnectorTransformerBOTest {

    @Test
    void applyPatientTransformationFiltersEachPatientGroupSeparately() {
        ConnectorTransformerDTO transformer = new ConnectorTransformerDTO();
        FilterPatientByHitFunction filter = new FilterPatientByHitFunction();
        FunctionRunnerBO functionRunnerBO = mock(FunctionRunnerBO.class);
        when(functionRunnerBO.apply(same(transformer), anyList(), isNull(), isNull()))
                .thenAnswer(invocation -> filter.apply(
                        invocation.getArgument(1),
                        null,
                        Map.of("value", "race"),
                        Map.of(),
                        Map.of("hit", "Caucasian", "case_sensitive", false),
                        null,
                        null,
                        null
                ));

        ConnectorTransformerBO bo = new ConnectorTransformerBO();
        bo.functionRunnerBO = functionRunnerBO;

        List<Map<String, Object>> rows = new ArrayList<>(List.of(
                row("patient-1", "AfricanAmerican"),
                row("patient-1", "Caucasian"),
                row("patient-2", "Hispanic"),
                row("patient-3", "Caucasian")
        ));

        List<Map<String, Object>> result = bo.applyPatientTransformation(transformer, rows, "patient_nbr");

        assertEquals(3, result.size());
        assertEquals("patient-1", result.get(0).get("patient_nbr"));
        assertEquals("patient-1", result.get(1).get("patient_nbr"));
        assertEquals("patient-3", result.get(2).get("patient_nbr"));
    }

    @Test
    void applyPatientTransformationRejectsRowsWithoutPatientId() {
        ConnectorTransformerBO bo = new ConnectorTransformerBO();
        bo.functionRunnerBO = mock(FunctionRunnerBO.class);

        assertThrows(BadRequestException.class, () -> bo.applyPatientTransformation(
                new ConnectorTransformerDTO(),
                List.of(row(null, "Caucasian")),
                "patient_nbr"
        ));
    }

    @Test
    void applyPatientTransformationRejectsMissingPatientIdColumn() {
        ConnectorTransformerBO bo = new ConnectorTransformerBO();
        bo.functionRunnerBO = mock(FunctionRunnerBO.class);

        assertThrows(BadRequestException.class, () -> bo.applyPatientTransformation(
                new ConnectorTransformerDTO(),
                List.of(row("patient-1", "Caucasian")),
                null
        ));
    }

    private static Map<String, Object> row(String patientId, String race) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("patient_nbr", patientId);
        row.put("race", race);
        return row;
    }
}
