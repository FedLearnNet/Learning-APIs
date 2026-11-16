package bio.cosy.feddb.transform.helper;

import bio.cosy.feddb.local.agent.etl.transform.helper.TransformerCombiner;
import bio.cosy.feddb.local.api.importer.functions.FunctionRegistry;
import bio.cosy.feddb.local.api.importer.functions.managed.builtin.FilterRowsByValueFunction;
import bio.cosy.feddb.local.api.importer.functions.managed.builtin.ReplaceValueFunction;
import bio.cosy.feddb.local.api.importer.transformer.ConnectorTransformerDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransformerCombinerTest {

    private final ReplaceValueFunction replaceValue = new ReplaceValueFunction();
    private final FilterRowsByValueFunction filterRows = new FilterRowsByValueFunction();

    @Mock
    FunctionRegistry functionRegistry;

    @Spy
    ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    TransformerCombiner combiner;

    @BeforeEach
    void setUp() {
        when(functionRegistry.get(any(), any())).thenAnswer(invocation -> Stream.of(replaceValue, filterRows)
                .filter(function -> function.methodName().equals(invocation.getArgument(1)))
                .findFirst());
    }

    @Test
    void combinesTheSameCellFunctionWithTheSameParameters() {
        List<ConnectorTransformerDTO> combined = combiner.combine(List.of(
                replaceMissing("payer_code"), replaceMissing("medical_specialty"), replaceMissing("weight")));

        assertEquals(1, combined.size());
        assertEquals("payer_code,medical_specialty,weight", combined.getFirst().getColumn());
    }

    @Test
    void keepsTransformersWithOtherParametersApart() {
        ConnectorTransformerDTO other = replaceMissing("weight");
        other.setInputMapping(Map.of("search", "[VALUE]None", "replace", "[VALUE]null"));

        List<ConnectorTransformerDTO> combined = combiner.combine(List.of(replaceMissing("payer_code"), other));

        assertEquals(2, combined.size());
    }

    @Test
    void keepsPatientFunctionsApart() {
        List<ConnectorTransformerDTO> combined = combiner.combine(List.of(filterRows("payer_code"), filterRows("weight")));

        assertEquals(2, combined.size());
    }

    @Test
    void doesNotMoveATransformerBeforeOneThatWritesItsColumn() {
        ConnectorTransformerDTO merge = new ConnectorTransformerDTO();
        merge.setModuleName("builtin");
        merge.setMethodName("Merge Columns");
        merge.setInputMapping(Map.of("first", "a", "second", "b"));
        merge.setReturnMapping(Map.of("value", "ab"));

        List<ConnectorTransformerDTO> combined = combiner.combine(List.of(
                replaceMissing("payer_code"), merge, replaceMissing("ab")));

        assertEquals(3, combined.size());
        assertEquals("payer_code", combined.getFirst().getColumn());
    }

    @Test
    void combinesAcrossTransformersThatDoNotTouchTheColumn() {
        ConnectorTransformerDTO date = new ConnectorTransformerDTO();
        date.setModuleName("builtin");
        date.setMethodName("datetime_to_date");
        date.setColumn("admission_date");

        List<ConnectorTransformerDTO> combined = combiner.combine(List.of(
                replaceMissing("payer_code"), date, replaceMissing("weight")));

        assertEquals(2, combined.size());
        assertEquals("payer_code,weight", combined.getFirst().getColumn());
    }

    @Test
    void movesOnlyTheColumnsNoTransformerInBetweenTouches() {
        ConnectorTransformerDTO date = new ConnectorTransformerDTO();
        date.setModuleName("builtin");
        date.setMethodName("datetime_to_date");
        date.setColumn("weight");

        List<ConnectorTransformerDTO> combined = combiner.combine(List.of(
                replaceMissing("payer_code"), date, replaceMissing("weight,medical_specialty")));

        assertEquals(3, combined.size());
        assertEquals("payer_code,medical_specialty", combined.getFirst().getColumn());
        assertEquals("weight", combined.getLast().getColumn());
    }

    @Test
    void movesInputParametersTheAgentPutIntoTheHyperparametersToTheInputMapping() {
        ConnectorTransformerDTO transformer = replaceMissingAsHyperparameters("payer_code");

        combiner.normalize(transformer);

        assertEquals(Map.of("search", "[VALUE]?", "replace", "[VALUE]null"), transformer.getInputMapping());
        assertEquals(Map.of(), transformer.getHyperparams());
    }

    @Test
    void combinesEqualTransformersTheAgentWroteInDifferentForms() {
        List<ConnectorTransformerDTO> combined = combiner.combine(List.of(
                replaceMissing("medical_specialty,weight"), replaceMissingAsHyperparameters("payer_code")));

        assertEquals(1, combined.size());
        assertEquals("medical_specialty,weight,payer_code", combined.getFirst().getColumn());
    }

    private ConnectorTransformerDTO filterRows(String column) {
        ConnectorTransformerDTO transformer = new ConnectorTransformerDTO();
        transformer.setModuleName(filterRows.module());
        transformer.setMethodName(filterRows.methodName());
        transformer.setColumn(column);
        transformer.setInputMapping(new HashMap<>(Map.of("value", "[VALUE]?")));
        return transformer;
    }

    private static ConnectorTransformerDTO replaceMissing(String column) {
        ConnectorTransformerDTO transformer = new ConnectorTransformerDTO();
        transformer.setModuleName("builtin");
        transformer.setMethodName("Replace Value");
        transformer.setColumn(column);
        transformer.setInputMapping(Map.of("search", "[VALUE]?", "replace", "[VALUE]null"));
        return transformer;
    }

    private static ConnectorTransformerDTO replaceMissingAsHyperparameters(String column) {
        ConnectorTransformerDTO transformer = new ConnectorTransformerDTO();
        transformer.setModuleName("builtin");
        transformer.setMethodName("Replace Value");
        transformer.setColumn(column);
        Map<String, Object> hyperparams = new HashMap<>();
        hyperparams.put("search", "?");
        hyperparams.put("replace", null);
        transformer.setHyperparams(hyperparams);
        return transformer;
    }
}
