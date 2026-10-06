package bio.cosy.feddb.local.agent.etl.transform.tools;

import bio.cosy.feddb.core.api.datamodler.datatype.DataTypeNodeDTO;
import bio.cosy.feddb.core.api.datamodler.datatype.DataTypes;
import bio.cosy.feddb.core.api.file.analytics.FileAnalytics;
import bio.cosy.feddb.local.agent.etl.transform.model.ConnectorSelectedTransformer;
import bio.cosy.feddb.local.agent.etl.transform.model.TransformerRunContext;
import bio.cosy.feddb.local.api.importer.connector.ConnectorConfigDTO;
import bio.cosy.feddb.local.api.importer.files.read.TableDataProfileBO;
import bio.cosy.feddb.local.api.importer.functions.managed.builtin.ReplaceValueFunction;
import bio.cosy.feddb.local.api.importer.run.preview.ConnectorPreviewRunnerBO;
import bio.cosy.feddb.local.api.importer.transformer.ConnectorTransformerDTO;
import bio.cosy.feddb.local.api.importer.validation.ConnectorValidationBO;
import bio.cosy.feddb.local.api.importer.validation.ConnectorValidationResultDTO;
import bio.cosy.feddb.local.api.importer.validation.PreviewValidationBO;
import bio.cosy.feddb.local.api.schema.LocalSchemaNodeDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TransformerValidateToolTest {

    private final TransformerValidateTool tool = new TransformerValidateTool();
    private TransformerRunContext context;
    private final ReplaceValueFunction replaceValue = new ReplaceValueFunction();
    private List<Map<String, Object>> previewRows;

    @BeforeEach
    void setUp() throws Exception {
        tool.previewRunnerBO = mock(ConnectorPreviewRunnerBO.class);
        when(tool.previewRunnerBO.run(any(), eq("keycloak-user"))).thenAnswer(invocation -> {
            ConnectorConfigDTO config = invocation.getArgument(0);
            List<Map<String, Object>> rows = previewRows.stream().<Map<String, Object>>map(LinkedHashMap::new).toList();
            for (ConnectorTransformerDTO transformer : config.getTransformer()) {
                Map<String, String> inputMapping = new HashMap<>();
                transformer.getInputMapping().forEach((key, value) -> inputMapping.put(key, String.valueOf(value)));
                rows = replaceValue.apply(rows, transformer.getColumn(), inputMapping, Map.of(),
                        transformer.getHyperparams(), null, null, null);
            }
            return rows;
        });
        tool.previewValidationBO = previewValidation();
        tool.listTool = mock(TransformerListTool.class);
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

        ConnectorValidationResultDTO result = tool.validate(
                ConnectorSelectedTransformer.mapping("payer_code", List.of(replace("?", "null"))));

        assertTrue(result.isValid(), result.getMessage());
    }

    @Test
    void runsTheTransformersOfEarlierNodesFirst() {
        init("payer_code", List.of("?", "MC", "?"), DataTypes.STRING, null);
        context.config().getTransformer().add(replace("?", "MC"));

        ConnectorValidationResultDTO result = tool.validate(
                ConnectorSelectedTransformer.mapping("payer_code", List.of(replace("MC", "Medicare"))));

        assertTrue(result.isValid(), result.getMessage());
    }

    @Test
    void rejectsUnknownStoreToolsWithoutStartingThem() {
        init("payer_code", List.of("MC"), DataTypes.STRING, null);
        ConnectorTransformerDTO storeTool = new ConnectorTransformerDTO();
        storeTool.setModuleName(TransformerListTool.TOOLS_MODULE);
        storeTool.setMethodName("Invented Tool");

        ConnectorValidationResultDTO result = tool.validate(
                ConnectorSelectedTransformer.mapping("payer_code", List.of(storeTool)));

        assertFalse(result.isValid());
        assertTrue(result.getMessage().startsWith("Unknown store tool"), result.getMessage());
        verify(tool.previewRunnerBO, never()).run(any(), any());
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
        previewRows = values.stream().<Map<String, Object>>map(value -> Map.of(column, value)).toList();
        ConnectorConfigDTO config = new ConnectorConfigDTO();
        config.setTransformer(new ArrayList<>());
        context = new TransformerRunContext(config, null, "keycloak-user", Map.of(), null);
        tool.init(context, node, dataType);
    }

    private ConnectorTransformerDTO replace(String search, String replacement) {
        ConnectorTransformerDTO transformer = new ConnectorTransformerDTO();
        transformer.setModuleName(replaceValue.module());
        transformer.setMethodName(replaceValue.methodName());
        transformer.setColumn("payer_code");
        transformer.setInputMapping(new LinkedHashMap<>(Map.of("search", "[VALUE]" + search, "replace", "[VALUE]" + replacement)));
        transformer.setHyperparams(new LinkedHashMap<>());
        return transformer;
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
