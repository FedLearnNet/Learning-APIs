package bio.cosy.feddb.local.agent.etl.transform.tools;

import bio.cosy.feddb.core.api.datamodler.datatype.DataTypeNodeDTO;
import bio.cosy.feddb.core.api.datamodler.datatype.DataTypes;
import bio.cosy.feddb.core.api.file.analytics.FileAnalytics;
import bio.cosy.feddb.local.agent.etl.transform.ColumnMatcherTest;
import bio.cosy.feddb.local.agent.etl.transform.model.ConnectorSelectedTransformer;
import bio.cosy.feddb.local.api.importer.files.read.TableDataProfileBO;
import bio.cosy.feddb.local.api.importer.files.table.TableSample;
import bio.cosy.feddb.local.api.importer.functions.FunctionRegistry;
import bio.cosy.feddb.local.api.importer.functions.managed.AbstractManagedFunction;
import bio.cosy.feddb.local.api.importer.functions.managed.builtin.GenericValueMapperFunction;
import bio.cosy.feddb.local.api.importer.functions.managed.builtin.ReplaceValueFunction;
import bio.cosy.feddb.local.api.importer.transformer.ConnectorTransformerBO;
import bio.cosy.feddb.local.api.importer.transformer.ConnectorTransformerDTO;
import bio.cosy.feddb.local.api.importer.validation.ConnectorValidationBO;
import bio.cosy.feddb.local.api.schema.LocalSchemaNodeDTO;
import bio.cosy.feddb.local.api.schema.datatype.DataTypeDTO;
import bio.cosy.feddb.local.api.schema.datatype.DataTypeOptionsDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TransformerValidateToolTest {

    private final TransformerValidateTool tool = new TransformerValidateTool();
    private DataTypeNodeDTO validationType;

    @BeforeEach
    void setUp() throws Exception {
        GenericValueMapperFunction valueMapper = new GenericValueMapperFunction();
        var objectMapperField = GenericValueMapperFunction.class.getDeclaredField("objectMapper");
        objectMapperField.setAccessible(true);
        objectMapperField.set(valueMapper, new ObjectMapper());

        tool.replaceValueFunction = new ReplaceValueFunction();
        tool.valueMapperFunction = valueMapper;
        tool.objectMapper = new ObjectMapper();
        tool.transformerBO = mock(ConnectorTransformerBO.class);
        when(tool.transformerBO.applyBuiltInTransformation(any(), any(), any())).thenAnswer(invocation -> {
            ConnectorTransformerDTO transformer = invocation.getArgument(0);
            AbstractManagedFunction function = transformer.getMethodName().equals(tool.replaceValueFunction.methodName())
                    ? tool.replaceValueFunction
                    : valueMapper;
            Map<String, String> inputMapping = new HashMap<>();
            transformer.getInputMapping().forEach((key, value) -> inputMapping.put(key, String.valueOf(value)));
            return function.apply(invocation.getArgument(1), transformer.getColumn(), inputMapping, Map.of(),
                    transformer.getHyperparams(), null, null, null);
        });
        tool.functionRegistry = mock(FunctionRegistry.class);
        when(tool.functionRegistry.get(any(), any())).thenAnswer(invocation ->
                Optional.of(tool.replaceValueFunction.methodName().equals(invocation.getArgument(1))
                        ? tool.replaceValueFunction
                        : valueMapper));
        tool.validationBO = new ConnectorValidationBO();
        tool.profileBO = new TableDataProfileBO(new FileAnalytics());
        tool.previewValidationBO = ColumnMatcherTest.previewValidation();
    }

    @Test
    void replacesASingleMissingValuePlaceholderWithReplaceValue() {
        init("payer_code", List.of("?", "MC", "?"), stringType());

        ConnectorSelectedTransformer repaired = tool.repair(ConnectorSelectedTransformer.mapping("payer_code", List.of()));

        assertNotNull(repaired);
        assertEquals("payer_code", repaired.getMappedColumn());
        assertEquals("Replace Value", repaired.getSelectedTransformers().getLast().getMethodName());
        assertEquals(Map.of("search", "[VALUE]?", "replace", "[VALUE]null"), repaired.getSelectedTransformers().getLast().getInputMapping());
        assertEquals(Map.of(), repaired.getSelectedTransformers().getLast().getHyperparams());
    }

    @Test
    void mapsLabelsAndPlaceholdersToAllowedValuesWithTheValueMapper() throws Exception {
        init("gender", List.of("Male", "female ", "?"), categorical());

        ConnectorSelectedTransformer repaired = tool.repair(ConnectorSelectedTransformer.mapping("gender", List.of()));

        assertNotNull(repaired);
        assertEquals("Generic Value Mapper", repaired.getSelectedTransformers().getLast().getMethodName());
        String map = String.valueOf(repaired.getSelectedTransformers().getLast().getInputMapping().get("map"));
        Map<String, Object> expected = new HashMap<>(Map.of("Male", "M", "female ", "F"));
        expected.put("?", null);
        assertEquals(expected, new ObjectMapper().readValue(map.substring("[VALUE]".length()), Map.class));
    }

    @Test
    void leavesValuesWithoutAKnownReplacementToTheLlm() {
        init("gender", List.of("M", "unknown gender"), categorical());

        assertNull(tool.repair(ConnectorSelectedTransformer.mapping("gender", List.of())));
    }

    private void init(String column, List<String> values, DataTypeDTO dataType) {
        LocalSchemaNodeDTO node = new LocalSchemaNodeDTO();
        node.setId(1L);
        node.setName(column);
        node.setDataType(dataType);
        validationType = new DataTypeNodeDTO();
        validationType.setType(dataType.getType());
        validationType.setName(dataType.getName());
        if (dataType.getAllowedValues() != null) {
            validationType.setOptions(dataType.getAllowedValues().stream().map(String::valueOf).toList());
        }
        List<Map<String, Object>> rows = values.stream().<Map<String, Object>>map(value -> Map.of(column, value)).toList();
        tool.init(new TableSample(List.of(column), rows, null), node, null, Map.of(), validationType);
    }

    private static DataTypeDTO stringType() {
        DataTypeDTO dataType = new DataTypeDTO();
        dataType.setName("text");
        dataType.setType(DataTypes.STRING);
        return dataType;
    }

    private static DataTypeDTO categorical() {
        DataTypeDTO dataType = new DataTypeDTO();
        dataType.setName("gender");
        dataType.setType(DataTypes.CATEGORICAL);
        dataType.setAllowedValues(new LinkedHashSet<>(List.of("M", "F")));
        dataType.setOptions(Set.of(new DataTypeOptionsDTO("Male", "M"), new DataTypeOptionsDTO("Female", "F")));
        dataType.setMapping(new HashMap<>(Map.of("M", "Male", "F", "Female")));
        return dataType;
    }
}
