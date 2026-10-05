package bio.cosy.feddb.local.agent.etl.transform.tools;

import bio.cosy.feddb.core.api.datamodler.datatype.DataTypeNodeDTO;
import bio.cosy.feddb.core.api.datamodler.datatype.DataTypes;
import bio.cosy.feddb.core.api.file.analytics.FileAnalytics;
import bio.cosy.feddb.local.agent.etl.transform.model.ConnectorSelectedTransformer;
import bio.cosy.feddb.local.api.importer.files.read.TableDataProfileBO;
import bio.cosy.feddb.local.api.importer.files.table.TableSample;
import bio.cosy.feddb.local.api.importer.functions.managed.builtin.ReplaceValueFunction;
import bio.cosy.feddb.local.api.importer.transformer.ConnectorTransformerBO;
import bio.cosy.feddb.local.api.importer.transformer.ConnectorTransformerDTO;
import bio.cosy.feddb.local.api.importer.validation.ConnectorValidationBO;
import bio.cosy.feddb.local.api.importer.validation.ConnectorValidationResultDTO;
import bio.cosy.feddb.local.api.importer.validation.PreviewValidationBO;
import bio.cosy.feddb.local.api.schema.LocalSchemaNodeDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TransformerValidateToolTest {

    private final TransformerValidateTool tool = new TransformerValidateTool();
    private final ReplaceValueFunction replaceValue = new ReplaceValueFunction();

    @BeforeEach
    void setUp() throws Exception {
        tool.transformerBO = mock(ConnectorTransformerBO.class);
        when(tool.transformerBO.applyBuiltInTransformations(any(), any(), any())).thenAnswer(invocation -> {
            List<ConnectorTransformerDTO> transformers = invocation.getArgument(0);
            List<Map<String, Object>> rows = invocation.getArgument(1);
            for (ConnectorTransformerDTO transformer : transformers) {
                Map<String, String> inputMapping = new HashMap<>();
                transformer.getInputMapping().forEach((key, value) -> inputMapping.put(key, String.valueOf(value)));
                rows = replaceValue.apply(rows, transformer.getColumn(), inputMapping, Map.of(),
                        transformer.getHyperparams(), null, null, null);
            }
            return rows;
        });
        tool.previewValidationBO = previewValidation();
    }

    @Test
    void rejectsMissingValuePlaceholdersOfADirectMapping() {
        init("payer_code", List.of("?", "MC", "?"), DataTypes.STRING, null);

        ConnectorValidationResultDTO result = tool.validate(ConnectorSelectedTransformer.mapping("payer_code", List.of()));

        assertFalse(result.isValid());
        assertTrue(result.getMessage().contains("'?': a missing-value placeholder"), result.getMessage());
    }

    @Test
    void acceptsTheChainOnceThePlaceholderIsReplaced() {
        init("payer_code", List.of("?", "MC", "?"), DataTypes.STRING, null);
        ConnectorTransformerDTO replace = new ConnectorTransformerDTO();
        replace.setModuleName(replaceValue.module());
        replace.setMethodName(replaceValue.methodName());
        replace.setColumn("payer_code");
        replace.setInputMapping(new LinkedHashMap<>(Map.of("search", "[VALUE]?", "replace", "[VALUE]null")));
        replace.setHyperparams(new LinkedHashMap<>());

        ConnectorValidationResultDTO result = tool.validate(ConnectorSelectedTransformer.mapping("payer_code", List.of(replace)));

        assertTrue(result.isValid(), result.getMessage());
    }

    @Test
    void rejectsLabelsOfACategoricalNode() {
        init("gender", List.of("M", "Male"), DataTypes.CATEGORICAL, List.of("M", "F"));

        ConnectorValidationResultDTO result = tool.validate(ConnectorSelectedTransformer.mapping("gender", List.of()));

        assertFalse(result.isValid());
        assertTrue(result.getMessage().startsWith("1 rejected values of 'gender'"), result.getMessage());
    }

    @Test
    void reportsAMappedColumnTheTransformationDoesNotProduce() {
        init("gender", List.of("M", "F"), DataTypes.CATEGORICAL, List.of("M", "F"));

        ConnectorValidationResultDTO result = tool.validate(ConnectorSelectedTransformer.mapping("sex", List.of()));

        assertFalse(result.isValid());
        assertTrue(result.getMessage().contains("does not exist after the transformation"), result.getMessage());
    }

    private void init(String column, List<String> values, DataTypes type, List<String> options) {
        LocalSchemaNodeDTO node = new LocalSchemaNodeDTO();
        node.setId(1L);
        node.setName(column);
        DataTypeNodeDTO dataType = new DataTypeNodeDTO();
        dataType.setName(column);
        dataType.setType(type);
        if (options != null) {
            dataType.setOptions(options);
        }
        List<Map<String, Object>> rows = values.stream().<Map<String, Object>>map(value -> Map.of(column, value)).toList();
        tool.init(new TableSample(List.of(column), rows, null), node, null, Map.of(), dataType);
    }

    private static PreviewValidationBO previewValidation() throws Exception {
        PreviewValidationBO previewValidation = new PreviewValidationBO();
        set(previewValidation, "validationBO", new ConnectorValidationBO());
        set(previewValidation, "profileBO", new TableDataProfileBO(new FileAnalytics()));
        set(previewValidation, "maxCategoricalValues", 50);
        return previewValidation;
    }

    private static void set(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
