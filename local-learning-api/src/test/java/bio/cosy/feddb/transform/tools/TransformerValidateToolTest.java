package bio.cosy.feddb.transform.tools;

import bio.cosy.feddb.core.api.datamodler.datatype.DataTypeNodeDTO;
import bio.cosy.feddb.core.api.datamodler.datatype.DataTypes;
import bio.cosy.feddb.core.api.file.analytics.ColumnProfile;
import bio.cosy.feddb.core.api.file.analytics.FileAnalytics;
import bio.cosy.feddb.local.api.importer.files.table.TableData;
import bio.cosy.feddb.local.agent.etl.transform.helper.AgentDataPrivacy;
import bio.cosy.feddb.local.agent.etl.transform.model.ConnectorSelectedTransformer;
import bio.cosy.feddb.local.agent.etl.transform.model.TransformerRunContext;
import bio.cosy.feddb.local.agent.etl.transform.tools.TransformerListTool;
import bio.cosy.feddb.local.agent.etl.transform.tools.TransformerValidateTool;
import bio.cosy.feddb.local.api.importer.connector.ConnectorConfigDTO;
import bio.cosy.feddb.local.api.importer.files.read.TableDataProfileBO;
import bio.cosy.feddb.local.api.importer.functions.managed.builtin.ReplaceValueFunction;
import bio.cosy.feddb.local.api.importer.run.preview.ConnectorPreviewRunnerBO;
import bio.cosy.feddb.local.api.importer.transformer.ConnectorTransformationBO;
import bio.cosy.feddb.local.api.importer.transformer.ConnectorTransformerDTO;
import bio.cosy.feddb.local.api.importer.transformer.ConnectorTransformerStatics;
import bio.cosy.feddb.local.api.importer.validation.ConnectorValidationBO;
import bio.cosy.feddb.local.api.importer.validation.ConnectorValidationResultDTO;
import bio.cosy.feddb.local.api.importer.validation.PreviewValidationBO;
import bio.cosy.feddb.local.api.schema.LocalSchemaNodeDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.*;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransformerValidateToolTest {

    private static final String ROW_FUNCTION = "Split Column";
    private static final String PATIENT_FUNCTION = "First Value Of Patient";

    private final ReplaceValueFunction replaceValue = new ReplaceValueFunction();
    private TransformerRunContext context;
    private LocalSchemaNodeDTO node;
    private DataTypeNodeDTO dataType;
    private List<Map<String, Object>> previewRows;

    @Mock
    ConnectorPreviewRunnerBO previewRunnerBO;

    @Mock
    TransformerListTool listTool;

    @Mock
    ConnectorTransformationBO transformationBO;

    @Mock
    AgentDataPrivacy dataPrivacy;

    @Spy
    PreviewValidationBO previewValidationBO = previewValidation();

    @InjectMocks
    TransformerValidateTool tool;

    @BeforeEach
    void setUp() {
        // Synthetic data is off, the agent sees the real rejected values
        lenient().when(dataPrivacy.rejected(any(), any())).thenAnswer(invocation -> invocation.getArgument(0));
        // The preview runs the Replace Value transformers on the rows; the unknown store tool never gets that far
        lenient().when(previewRunnerBO.run(any(), eq("keycloak-user"))).thenAnswer(invocation -> {
            ConnectorConfigDTO config = invocation.getArgument(0);
            List<Map<String, Object>> rows = previewRows.stream().<Map<String, Object>>map(LinkedHashMap::new).toList();
            for (ConnectorTransformerDTO transformer : config.getTransformer()) {
                rows = replace(transformer, rows);
            }
            return rows;
        });
        lenient().when(listTool.isBuiltInFunction(any())).thenAnswer(invocation -> Set.of(replaceValue.methodName(), ROW_FUNCTION,
                PATIENT_FUNCTION).contains(invocation.<ConnectorTransformerDTO>getArgument(0).getMethodName()));
        lenient().when(transformationBO.applyBuiltInTransformations(any(), any(), any())).thenAnswer(invocation -> {
            List<ConnectorTransformerDTO> transformers = invocation.getArgument(0);
            List<Map<String, Object>> rows = invocation.getArgument(1);
            for (ConnectorTransformerDTO transformer : transformers) {
                rows = replace(transformer, rows);
            }
            return rows;
        });
    }

    @Test
    void rejectsMissingValuePlaceholdersOfADirectMapping() {
        init("payer_code", List.of("?", "MC", "?"), DataTypes.STRING, null);

        ConnectorValidationResultDTO result = tool.validate(ConnectorSelectedTransformer.mapping("payer_code", List.of()));

        assertFalse(result.isValid());
        assertTrue(result.getMessage().contains("'?': a missing-value placeholder"), result.getMessage());
    }

    @Test
    void rejectsAChainThatEmptiesEveryValue() {
        init("payer_code", List.of("MC", "HM"), DataTypes.STRING, null);
        withFileProfiles("payer_code", List.of("MC", "HM"));

        ConnectorValidationResultDTO result = tool.validate(
                ConnectorSelectedTransformer.mapping("payer_code", List.of(replace("MC", "null"), replace("HM", "null"))));

        assertFalse(result.isValid());
        assertTrue(result.getMessage().startsWith("Every value of 'payer_code' is empty"), result.getMessage());
    }

    @Test
    void acceptsEmptyingAColumnOfPlaceholdersOnly() {
        init("payer_code", List.of("?", "?"), DataTypes.STRING, null);
        withFileProfiles("payer_code", List.of("?", "?"));

        ConnectorValidationResultDTO result = tool.validate(
                ConnectorSelectedTransformer.mapping("payer_code", List.of(replace("?", "null"))));

        assertTrue(result.isValid(), result.getMessage());
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
        storeTool.setModuleName(ConnectorTransformerStatics.TOOLS_MODULE);
        storeTool.setMethodName("Invented Tool");

        ConnectorValidationResultDTO result = tool.validate(
                ConnectorSelectedTransformer.mapping("payer_code", List.of(storeTool)));

        assertFalse(result.isValid());
        assertTrue(result.getMessage().startsWith("Unknown store tool"), result.getMessage());
        verify(previewRunnerBO, never()).run(any(), any());
    }

    @Test
    void rejectsAFunctionThatDoesNotExist() {
        init("payer_code", List.of("MC"), DataTypes.STRING, null);
        ConnectorTransformerDTO invented = replace("?", "null");
        invented.setModuleName("python");
        invented.setMethodName("transform");

        ConnectorValidationResultDTO result = tool.validate(
                ConnectorSelectedTransformer.mapping("payer_code", List.of(invented)));

        assertFalse(result.isValid());
        assertTrue(result.getMessage().startsWith("Unknown functions [python/transform]"), result.getMessage());
        verify(previewRunnerBO, never()).run(any(), any());
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

    @Test
    void rejectsAColumnThatAlreadyHoldsAnotherNode() {
        init("payer_code", List.of("MC"), DataTypes.STRING, null);
        context.mappedColumns().put("payer_code", "Encounter.Payer");

        ConnectorValidationResultDTO result = tool.validate(ConnectorSelectedTransformer.mapping("payer_code", List.of()));

        assertFalse(result.isValid());
        assertTrue(result.getMessage().contains("already holds Encounter.Payer"), result.getMessage());
    }

    @Test
    void rejectsFileValuesThePreviewRowsDoNotShow() {
        init("payer_code", List.of("?", "MC"), DataTypes.STRING, null);
        tool.init(context, node, dataType, List.of(Map.of("payer_code", "?"), Map.of("payer_code", "-")));

        ConnectorValidationResultDTO result = tool.validate(
                ConnectorSelectedTransformer.mapping("payer_code", List.of(replace("?", "null"))));

        assertFalse(result.isValid());
        assertTrue(result.getMessage().contains("'-'"), result.getMessage());
    }

    @Test
    void acceptsARowFunctionWithSeveralInputsAndOutputs() {
        initMedications();

        ConnectorValidationResultDTO result = tool.validate(ConnectorSelectedTransformer.mapping("combination",
                List.of(function(ROW_FUNCTION, Map.of("first", "metformin_rx", "second", "rosiglitazone_rx"),
                        Map.of("combination", "combination", "rest", "rest")))));

        assertTrue(result.isValid(), result.getMessage());
    }

    @Test
    void readsAColumnOfAnotherNodeIntoANewColumn() {
        initMedications();
        context.mappedColumns().put("metformin_rx", "metformin");

        ConnectorValidationResultDTO result = tool.validate(ConnectorSelectedTransformer.mapping("combination",
                List.of(function(PATIENT_FUNCTION, Map.of("value", "metformin_rx"), Map.of("value", "combination")))));

        assertTrue(result.isValid(), result.getMessage());
    }

    @Test
    void rejectsWritingTheColumnOfAnotherNode() {
        initMedications();
        context.mappedColumns().put("metformin_rx", "metformin");

        ConnectorValidationResultDTO result = tool.validate(ConnectorSelectedTransformer.mapping("metformin_rx",
                List.of(function(ROW_FUNCTION, Map.of("first", "rosiglitazone_rx"), Map.of("value", "metformin_rx")))));

        assertFalse(result.isValid());
        assertTrue(result.getMessage().contains("already holds metformin") && result.getMessage().contains("new output column"),
                result.getMessage());
    }

    @Test
    void readsTheOutputOfAnEarlierTransformer() {
        initMedications();

        ConnectorValidationResultDTO result = tool.validate(ConnectorSelectedTransformer.mapping("combination", List.of(
                function(ROW_FUNCTION, Map.of("first", "metformin_rx"), Map.of("value", "copy")),
                function(PATIENT_FUNCTION, Map.of("value", "copy"), Map.of("value", "combination")))));

        assertTrue(result.isValid(), result.getMessage());
    }

    private void initMedications() {
        init("metformin_rx", List.of("No", "Up"), DataTypes.STRING, null);
        previewRows = List.of(Map.of("patient", "p1", "metformin_rx", "No", "rosiglitazone_rx", "Up"));
        tool.init(context, node, dataType, previewRows);
    }

    private static ConnectorTransformerDTO function(String method, Map<String, Object> inputMapping, Map<String, Object> returnMapping) {
        ConnectorTransformerDTO transformer = new ConnectorTransformerDTO();
        transformer.setModuleName("builtin");
        transformer.setMethodName(method);
        transformer.setInputMapping(new LinkedHashMap<>(inputMapping));
        transformer.setReturnMapping(new LinkedHashMap<>(returnMapping));
        transformer.setHyperparams(new LinkedHashMap<>(PATIENT_FUNCTION.equals(method) ? Map.of("patient_id_column", "patient") : Map.of()));
        return transformer;
    }

    private void init(String column, List<String> values, DataTypes type, List<String> options) {
        node = new LocalSchemaNodeDTO();
        node.setId(1L);
        node.setName(column);
        dataType = new DataTypeNodeDTO();
        dataType.setName(column);
        dataType.setType(type);
        if (options != null) {
            dataType.setOptions(options);
        }
        previewRows = values.stream().<Map<String, Object>>map(value -> Map.of(column, value)).toList();
        ConnectorConfigDTO config = new ConnectorConfigDTO();
        config.setTransformer(new ArrayList<>());
        context = new TransformerRunContext(config, null, "keycloak-user", Map.of(), new HashMap<>(), new ArrayList<>(), new ArrayList<>(), null);
        tool.init(context, node, dataType, List.of());
    }

    private void withFileProfiles(String column, List<String> values) {
        List<Map<String, Object>> rows = values.stream().<Map<String, Object>>map(value -> new LinkedHashMap<>(Map.of(column, value))).toList();
        try (TableData data = new TableData(new ArrayList<>(List.of(column)), new ArrayList<>(rows), null)) {
            Map<String, ColumnProfile> profiles = new HashMap<>();
            new TableDataProfileBO(new FileAnalytics()).profiles(data).forEach(profile -> profiles.put(profile.name(), profile));
            context = new TransformerRunContext(context.config(), null, "keycloak-user", profiles, new HashMap<>(),
                    new ArrayList<>(), new ArrayList<>(), null);
        }
        tool.init(context, node, dataType, List.of());
    }

    private List<Map<String, Object>> replace(ConnectorTransformerDTO transformer, List<Map<String, Object>> rows) {
        if (!replaceValue.methodName().equals(transformer.getMethodName())) {
            // A ROW or PATIENT function: every output gets the value of its first input
            String input = String.valueOf(transformer.getInputMapping().values().iterator().next());
            return rows.stream().<Map<String, Object>>map(row -> {
                Map<String, Object> copy = new LinkedHashMap<>(row);
                transformer.getReturnMapping().values().forEach(output -> copy.put(String.valueOf(output), row.get(input)));
                return copy;
            }).toList();
        }
        Map<String, String> inputMapping = new HashMap<>();
        transformer.getInputMapping().forEach((key, value) -> inputMapping.put(key, String.valueOf(value)));
        return replaceValue.apply(rows, transformer.getColumn(), inputMapping, Map.of(),
                transformer.getHyperparams(), null, null, null);
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

    private static PreviewValidationBO previewValidation() {
        PreviewValidationBO previewValidation = new PreviewValidationBO();
        set(previewValidation, "validationBO", new ConnectorValidationBO());
        set(previewValidation, "profileBO", new TableDataProfileBO(new FileAnalytics()));
        set(previewValidation, "maxCategoricalValues", 50);
        return previewValidation;
    }

    private static void set(Object target, String name, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
