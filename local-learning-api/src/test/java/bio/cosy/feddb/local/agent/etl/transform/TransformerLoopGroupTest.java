package bio.cosy.feddb.local.agent.etl.transform;

import bio.cosy.feddb.local.agent.etl.transform.organizer.TransformerLoop;
import bio.cosy.feddb.local.api.importer.transformer.ConnectorTransformerDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TransformerLoopGroupTest {

    private final TransformerLoop loop = new TransformerLoop();

    {
        loop.objectMapper = new ObjectMapper();
    }

    @Test
    void groupsTheSameCellFunctionWithTheSameParameters() {
        List<ConnectorTransformerDTO> grouped = loop.group(List.of(
                replaceMissing("payer_code"), replaceMissing("medical_specialty"), replaceMissing("weight")));

        assertEquals(1, grouped.size());
        assertEquals("payer_code,medical_specialty,weight", grouped.getFirst().getColumn());
    }

    @Test
    void keepsTransformersWithOtherParametersApart() {
        ConnectorTransformerDTO other = replaceMissing("weight");
        other.setInputMapping(Map.of("search", "[VALUE]None", "replace", "[VALUE]null"));

        List<ConnectorTransformerDTO> grouped = loop.group(List.of(replaceMissing("payer_code"), other));

        assertEquals(2, grouped.size());
    }

    @Test
    void doesNotMoveATransformerBeforeOneThatWritesItsColumn() {
        ConnectorTransformerDTO merge = new ConnectorTransformerDTO();
        merge.setModuleName("builtin");
        merge.setMethodName("Merge Columns");
        merge.setInputMapping(Map.of("first", "a", "second", "b"));
        merge.setReturnMapping(Map.of("value", "ab"));

        List<ConnectorTransformerDTO> grouped = loop.group(List.of(
                replaceMissing("payer_code"), merge, replaceMissing("ab")));

        assertEquals(3, grouped.size());
        assertEquals("payer_code", grouped.getFirst().getColumn());
    }

    @Test
    void groupsAcrossTransformersThatDoNotTouchTheColumn() {
        ConnectorTransformerDTO date = new ConnectorTransformerDTO();
        date.setModuleName("builtin");
        date.setMethodName("datetime_to_date");
        date.setColumn("admission_date");

        List<ConnectorTransformerDTO> grouped = loop.group(List.of(
                replaceMissing("payer_code"), date, replaceMissing("weight")));

        assertEquals(2, grouped.size());
        assertEquals("payer_code,weight", grouped.getFirst().getColumn());
    }

    private static ConnectorTransformerDTO replaceMissing(String column) {
        ConnectorTransformerDTO transformer = new ConnectorTransformerDTO();
        transformer.setModuleName("builtin");
        transformer.setMethodName("Replace Value");
        transformer.setColumn(column);
        transformer.setInputMapping(Map.of("search", "[VALUE]?", "replace", "[VALUE]null"));
        return transformer;
    }
}
