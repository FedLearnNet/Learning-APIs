package bio.cosy.feddb.local.agent.etl.transform.tools;

import bio.cosy.feddb.core.api.datamodler.datatype.DataTypeNodeDTO;
import bio.cosy.feddb.core.api.file.analytics.ColumnProfile;
import bio.cosy.feddb.core.api.file.analytics.RowProfiler;
import bio.cosy.feddb.local.agent.etl.transform.model.ConnectorSelectedTransformer;
import bio.cosy.feddb.local.api.importer.files.read.TableDataProfileBO;
import bio.cosy.feddb.local.api.importer.files.table.TableData;
import bio.cosy.feddb.local.api.importer.files.table.TableSample;
import bio.cosy.feddb.local.api.importer.functions.FunctionParameterDTO;
import bio.cosy.feddb.local.api.importer.functions.FunctionParameterUsage;
import bio.cosy.feddb.local.api.importer.functions.FunctionRegistry;
import bio.cosy.feddb.local.api.importer.functions.FunctionsDetailDTO;
import bio.cosy.feddb.local.api.importer.functions.managed.AbstractManagedFunction;
import bio.cosy.feddb.local.api.importer.functions.managed.builtin.GenericValueMapperFunction;
import bio.cosy.feddb.local.api.importer.functions.managed.builtin.ReplaceValueFunction;
import bio.cosy.feddb.local.api.importer.transformer.ConnectorTransformerBO;
import bio.cosy.feddb.local.api.importer.transformer.ConnectorTransformerDTO;
import bio.cosy.feddb.local.api.importer.validation.ConnectorValidationBO;
import bio.cosy.feddb.local.api.importer.validation.ConnectorValidationResultDTO;
import bio.cosy.feddb.local.api.importer.validation.PreviewValidationBO;
import bio.cosy.feddb.local.api.schema.LocalSchemaNodeDTO;
import bio.cosy.feddb.local.api.schema.datatype.DataTypeDTO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agentic.Agent;
import io.quarkus.arc.Arc;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@RequestScoped
public class TransformerValidateTool {

    public static final String VALUE_PREFIX = "[VALUE]";
    private static final int MAX_REPORTED_ERRORS = 10;
    private static final Object NO_REPLACEMENT = new Object();
    private static final Set<String> EXTRA_PLACEHOLDERS = Set.of("?", "-", "--", ".");

    @Inject
    ConnectorTransformerBO transformerBO;

    @Inject
    ConnectorValidationBO validationBO;

    @Inject
    PreviewValidationBO previewValidationBO;

    @Inject
    TableDataProfileBO profileBO;

    @Inject
    ReplaceValueFunction replaceValueFunction;

    @Inject
    GenericValueMapperFunction valueMapperFunction;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    FunctionRegistry functionRegistry;

    @Inject
    TransformerListTool listTool;

    private TableSample previewData;
    private LocalSchemaNodeDTO schemaNode;
    private String patientIdColumn;
    private Map<String, ColumnProfile> fileProfiles;
    private DataTypeNodeDTO dataType;

    public void init(TableSample previewData, LocalSchemaNodeDTO schemaNode, String patientIdColumn,
                     Map<String, ColumnProfile> fileProfiles, DataTypeNodeDTO dataType) {
        this.previewData = previewData;
        this.schemaNode = schemaNode;
        this.patientIdColumn = patientIdColumn;
        this.fileProfiles = fileProfiles;
        this.dataType = dataType;
    }

    public static boolean isMissingPlaceholder(Object value, Collection<?> allowedValues) {
        String text = value == null ? "" : String.valueOf(value).strip();
        return !text.isEmpty()
                && (RowProfiler.isMissing(text) || EXTRA_PLACEHOLDERS.contains(text))
                && (allowedValues == null || allowedValues.stream().map(String::valueOf).noneMatch(text::equals));
    }

    public static String rejection(ConnectorValidationBO validationBO, Object value, Long schemaId, DataTypeNodeDTO dataType) {
        ConnectorValidationResultDTO result = validationBO.validate(value, schemaId, dataType);
        if (!result.isValid()) {
            return result.getMessage();
        }
        return isMissingPlaceholder(value, dataType == null ? null : dataType.getOptions())
                ? "a missing-value placeholder, replace it with null (Replace Value function)"
                : null;
    }

    @Tool("Validate transformers before answering: runs them in order on the preview data and checks the values of mappedColumn against the schema node. Pass an empty list for a direct mapping.")
    public ConnectorValidationResultDTO validateTransformers(
            @P(value = "The transformers in execution order", required = false) List<ConnectorTransformerDTO> transformers,
            @P("The column mapped onto the schema node") String mappedColumn) {
        return validate(ConnectorSelectedTransformer.mapping(mappedColumn, transformers));
    }

    public ConnectorValidationResultDTO validate(ConnectorSelectedTransformer selected) {
        Long schemaId = schemaNode.getId();
        if (selected == null || !selected.isFoundTransformer()) {
            return new ConnectorValidationResultDTO("No transformer selected", schemaId);
        }
        selected.getSelectedTransformers().forEach(this::normalizeParameters);
        ConnectorValidationResultDTO storeToolResult = validateStoreTools(selected);
        if (storeToolResult != null) {
            return storeToolResult;
        }
        List<Map<String, Object>> rows;
        try {
            rows = transform(selected);
        } catch (RuntimeException e) {
            return new ConnectorValidationResultDTO("Running the transformer failed: " + e.getMessage(), schemaId);
        }
        String column = selected.getMappedColumn();
        if (column == null || rows.stream().noneMatch(row -> row.containsKey(column))) {
            return new ConnectorValidationResultDTO("mappedColumn '" + column + "' does not exist after the transformation", schemaId);
        }
        Map<Object, String> rejected = rejectedValues(rows, selected);
        return rejected.isEmpty()
                ? new ConnectorValidationResultDTO(schemaId)
                : new ConnectorValidationResultDTO(rejected.size() + " rejected values of '" + column + "':\n"
                + rejected.entrySet().stream().limit(MAX_REPORTED_ERRORS)
                .map(entry -> "'" + entry.getKey() + "': " + entry.getValue())
                .collect(Collectors.joining("\n")), schemaId);
    }

    public ConnectorSelectedTransformer repair(ConnectorSelectedTransformer selected) {
        String column = selected.getMappedColumn();
        List<Map<String, Object>> rows;
        try {
            rows = transform(selected);
        } catch (RuntimeException e) {
            return null;
        }
        Map<String, Object> replacements = new LinkedHashMap<>();
        for (Object value : rejectedValues(rows, selected).keySet()) {
            Object replacement = replacementFor(value);
            if (replacement == NO_REPLACEMENT) {
                return null;
            }
            replacements.put(String.valueOf(value), replacement);
        }
        if (replacements.isEmpty()) {
            return null;
        }
        List<ConnectorTransformerDTO> chain = new ArrayList<>(selected.getSelectedTransformers());
        chain.add(replacements.size() == 1
                ? replaceValue(column, replacements.entrySet().iterator().next())
                : valueMapper(column, replacements));
        ConnectorSelectedTransformer repaired = ConnectorSelectedTransformer.mapping(column, chain);
        return validate(repaired).isValid() ? repaired : null;
    }

    private ConnectorValidationResultDTO validateStoreTools(ConnectorSelectedTransformer selected) {
        List<ConnectorTransformerDTO> storeTools = selected.getSelectedTransformers().stream()
                .filter(transformer -> TransformerListTool.TOOLS_MODULE.equals(transformer.getModuleName())
                        || transformer.getAppVersionId() != null
                        || transformer.getAppImage() != null && !transformer.getAppImage().isBlank())
                .toList();
        if (storeTools.isEmpty()) {
            return null;
        }
        for (ConnectorTransformerDTO transformer : storeTools) {
            Optional<FunctionsDetailDTO> tool = listTool.storeTool(transformer.getAppVersionId());
            if (tool.isEmpty()) {
                return new ConnectorValidationResultDTO("Unknown store tool '" + transformer.getMethodName()
                        + "'. Copy appVersionId and appImage exactly from getAllStoreDetailed.", schemaNode.getId());
            }
            transformer.setModuleName(TransformerListTool.TOOLS_MODULE);
            transformer.setMethodName(tool.get().getMethodName());
            transformer.setAppImage(tool.get().getAppImage());
        }
        ConnectorValidationResultDTO result = new ConnectorValidationResultDTO(schemaNode.getId());
        result.setMessage("Store tool " + storeTools.getFirst().getMethodName()
                + " runs in a container, so the chain is validated when the connector runs");
        return result;
    }

    private void normalizeParameters(ConnectorTransformerDTO transformer) {
        if (transformer == null) {
            return;
        }
        functionRegistry.get(transformer.getModuleName(), transformer.getMethodName()).ifPresent(function -> {
            Map<String, Object> inputMapping = new LinkedHashMap<>(transformer.getInputMapping() == null ? Map.of() : transformer.getInputMapping());
            Map<String, Object> hyperparams = new LinkedHashMap<>(transformer.getHyperparams() == null ? Map.of() : transformer.getHyperparams());
            for (FunctionParameterDTO parameter : function.parameters()) {
                String name = parameter.getName();
                if (parameter.getUsage() == FunctionParameterUsage.HYPERPARAMETER) {
                    Object value = inputMapping.get(name);
                    if (value instanceof String text && text.startsWith(VALUE_PREFIX)) {
                        inputMapping.remove(name);
                        hyperparams.putIfAbsent(name, text.substring(VALUE_PREFIX.length()));
                    }
                } else if (hyperparams.containsKey(name) && !asText(hyperparams.get(name)).isEmpty()) {
                    inputMapping.putIfAbsent(name, VALUE_PREFIX + asText(hyperparams.remove(name)));
                }
            }
            transformer.setInputMapping(inputMapping);
            transformer.setHyperparams(hyperparams);
        });
    }

    private String asText(Object value) {
        if (value == null || value instanceof String) {
            return value == null ? "null" : (String) value;
        }
        try {
            return value instanceof Map || value instanceof Collection ? objectMapper.writeValueAsString(value) : String.valueOf(value);
        } catch (JsonProcessingException e) {
            return String.valueOf(value);
        }
    }

    private Map<Object, String> rejectedValues(List<Map<String, Object>> rows, ConnectorSelectedTransformer selected) {
        String column = selected.getMappedColumn();
        ColumnProfile profile = selected.getSelectedTransformers().isEmpty() && fileProfiles.containsKey(column)
                ? fileProfiles.get(column)
                : profileBO.profiles(new TableData(new ArrayList<>(List.of(column)), rows, null)).stream().findFirst().orElse(null);
        Map<Object, String> rejected = new LinkedHashMap<>();
        if (profile == null) {
            rejected.put("", "the values of this column could not be profiled");
            return rejected;
        }
        for (String value : previewValidationBO.values(profile)) {
            String message = rejection(validationBO, value, schemaNode.getId(), dataType);
            if (message != null) {
                rejected.put(value, message);
            }
        }
        return rejected;
    }

    private Object replacementFor(Object value) {
        if (value == null || String.valueOf(value).isBlank()) {
            return NO_REPLACEMENT;
        }
        if (isMissingPlaceholder(value, dataType == null ? null : dataType.getOptions())) {
            return validationBO.validate(null, schemaNode.getId(), dataType).isValid() ? null : NO_REPLACEMENT;
        }
        Map<String, Object> allowedByText = new HashMap<>();
        DataTypeDTO schemaType = schemaNode.getDataType();
        if (schemaType != null) {
            if (schemaType.getAllowedValues() != null) {
                schemaType.getAllowedValues().forEach(allowed -> allowedByText.put(text(allowed), allowed));
            }
            if (schemaType.getOptions() != null) {
                schemaType.getOptions().forEach(option -> allowedByText.put(text(option.getLabel()), option.getValue()));
            }
            if (schemaType.getMapping() != null) {
                schemaType.getMapping().forEach((allowed, label) -> allowedByText.put(text(label), allowed));
            }
        }
        Object replacement = allowedByText.getOrDefault(text(value), NO_REPLACEMENT);
        return replacement != NO_REPLACEMENT && validationBO.validate(replacement, schemaNode.getId(), dataType).isValid()
                ? replacement
                : NO_REPLACEMENT;
    }

    private ConnectorTransformerDTO replaceValue(String column, Map.Entry<String, Object> replacement) {
        ConnectorTransformerDTO transformer = cellTransformer(replaceValueFunction, column);
        transformer.setHyperparams(new LinkedHashMap<>(Map.of(
                "search", replacement.getKey(),
                "replace", replacement.getValue() == null ? "null" : String.valueOf(replacement.getValue()))));
        return transformer;
    }

    private ConnectorTransformerDTO valueMapper(String column, Map<String, Object> replacements) {
        ConnectorTransformerDTO transformer = cellTransformer(valueMapperFunction, column);
        try {
            transformer.setHyperparams(new LinkedHashMap<>(Map.of("map", objectMapper.writeValueAsString(replacements))));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not write the value map", e);
        }
        return transformer;
    }

    private static ConnectorTransformerDTO cellTransformer(AbstractManagedFunction function, String column) {
        ConnectorTransformerDTO transformer = new ConnectorTransformerDTO();
        transformer.setModuleName(function.module());
        transformer.setMethodName(function.methodName());
        transformer.setColumn(column);
        return transformer;
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value).strip().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    @Agent(description = "Validates the selected transformer on the preview data", outputKey = "validation")
    public static ConnectorValidationResultDTO validateSelected(ConnectorSelectedTransformer selectedTransformer) {
        return Arc.container().select(TransformerValidateTool.class).get().validate(selectedTransformer);
    }

    private List<Map<String, Object>> transform(ConnectorSelectedTransformer selected) {
        List<Map<String, Object>> rows = previewData.toTableData().getRows().stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(ArrayList::new));
        for (ConnectorTransformerDTO transformer : selected.getSelectedTransformers()) {
            rows = transformerBO.applyBuiltInTransformation(transformer, rows, patientIdColumn);
        }
        return rows;
    }
}
