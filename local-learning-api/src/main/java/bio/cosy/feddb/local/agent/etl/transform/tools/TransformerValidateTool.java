package bio.cosy.feddb.local.agent.etl.transform.tools;

import bio.cosy.feddb.core.api.datamodler.datatype.DataTypeNodeDTO;
import bio.cosy.feddb.core.api.file.analytics.ColumnProfile;
import bio.cosy.feddb.local.agent.etl.transform.helper.EtlAgentLog;
import bio.cosy.feddb.local.agent.etl.transform.helper.AgentDataPrivacy;
import bio.cosy.feddb.local.agent.etl.transform.helper.TransformerHelper;
import bio.cosy.feddb.local.agent.etl.transform.model.ConnectorSelectedTransformer;
import bio.cosy.feddb.local.agent.etl.transform.model.TransformerRunContext;
import bio.cosy.feddb.local.api.importer.connector.ConnectorHelper;
import bio.cosy.feddb.local.api.importer.functions.managed.AbstractManagedFunction;
import bio.cosy.feddb.local.api.importer.run.preview.ConnectorPreviewRunnerBO;
import bio.cosy.feddb.local.api.importer.transformer.ConnectorTransformationBO;
import bio.cosy.feddb.local.api.importer.transformer.ConnectorTransformerDTO;
import bio.cosy.feddb.local.api.importer.validation.ConnectorValidationBO;
import bio.cosy.feddb.local.api.importer.validation.ConnectorValidationResultDTO;
import bio.cosy.feddb.local.api.importer.validation.PreviewValidationBO;
import bio.cosy.feddb.local.api.schema.LocalSchemaNodeDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agentic.Agent;
import io.quarkus.arc.Arc;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

@RequestScoped
public class TransformerValidateTool {

    private static final int MAX_REPORTED_ERRORS = 10;
    private static final int MAX_REPORTED_CONVERSIONS = 30;

    @Inject
    AgentDataPrivacy dataPrivacy;

    @Inject
    ConnectorPreviewRunnerBO previewRunnerBO;

    @Inject
    PreviewValidationBO previewValidationBO;

    @Inject
    TransformerListTool listTool;

    @Inject
    ConnectorTransformationBO transformationBO;

    @Inject
    ObjectMapper objectMapper;

    private TransformerRunContext context;
    private LocalSchemaNodeDTO schemaNode;
    private DataTypeNodeDTO dataType;
    private List<String> selectedColumns;
    private List<Map<String, Object>> valueRows;
    // The value rows after the transformers of the last check, null when they did not run on them
    private List<Map<String, Object>> convertedValueRows;

    public void init(TransformerRunContext context, LocalSchemaNodeDTO schemaNode, DataTypeNodeDTO dataType,
                     List<String> selectedColumns, List<Map<String, Object>> valueRows) {
        this.context = context;
        this.schemaNode = schemaNode;
        this.dataType = dataType;
        this.selectedColumns = selectedColumns;
        this.valueRows = valueRows;
    }

    @Tool("Validate transformers before answering: runs them in order on the preview data, store tools in a container, and checks the values of mappedColumn against the schema node. Pass an empty list for a direct mapping.")
    public ConnectorValidationResultDTO validateTransformers(
            @P(value = "The transformers in execution order", required = false) List<ConnectorTransformerDTO> transformers,
            @P("The column mapped onto the schema node") String mappedColumn) {
        return validate(ConnectorSelectedTransformer.mapping(mappedColumn, transformers));
    }

    @Agent(description = "Validates the selected transformer on the preview data", outputKey = "validation")
    public static ConnectorValidationResultDTO validateSelected(ConnectorSelectedTransformer selectedTransformer) {
        return Arc.container().select(TransformerValidateTool.class).get().validateAnswer(selectedTransformer);
    }

    public ConnectorValidationResultDTO validateAnswer(ConnectorSelectedTransformer selected) {
        ConnectorValidationResultDTO result = validate(selected);
        if (!result.isValid() && selected != null && selected.isFoundTransformer()) {
            result.setMessage("Your previous answer " + json(selected) + " is rejected: " + result.getMessage());
        }
        return result;
    }

    public ConnectorValidationResultDTO validate(ConnectorSelectedTransformer selected) {
        ConnectorValidationResultDTO result = check(selected);
        context.step("%s %s", EtlAgentLog.describe(selected),
                result.isValid() ? "valid" : "rejected (" + EtlAgentLog.shorten(result.getMessage()) + ")");
        return result;
    }

    private ConnectorValidationResultDTO check(ConnectorSelectedTransformer selected) {
        Long schemaId = schemaNode.getId();
        convertedValueRows = null;
        if (selected == null || !selected.isFoundTransformer()) {
            return new ConnectorValidationResultDTO("No transformer selected", schemaId);
        }
        List<ConnectorTransformerDTO> transformers = selected.getSelectedTransformers();
        boolean knownStoreTools = transformers.stream()
                .filter(ConnectorTransformerDTO::isStoreTool)
                .allMatch(transformer -> listTool.resolveAppTransformer(transformer, context.patientIdColumn()));
        if (!knownStoreTools) {
            return new ConnectorValidationResultDTO("Unknown store tool or custom code transformer. Copy appVersionId "
                    + "and appImage exactly from getAllStoreDetailed, moduleName, methodName and appImage from getAllCodeDetailed.", schemaId);
        }
        List<String> unknownFunctions = transformers.stream()
                .filter(transformer -> !transformer.isStoreTool())
                .filter(transformer -> !listTool.isBuiltInFunction(transformer))
                .map(transformer -> transformer.getModuleName() + "/" + transformer.getMethodName())
                .toList();
        if (!unknownFunctions.isEmpty()) {
            return new ConnectorValidationResultDTO("Unknown functions " + unknownFunctions + ". Copy moduleName and "
                    + "methodName exactly from getAllDetail, or describe the needed function with foundTransformer false.", schemaId);
        }

        String column = selected.getMappedColumn();
        String mappedBefore = context.mappedColumns().get(column);
        if (mappedBefore != null) {
            return new ConnectorValidationResultDTO("Column '" + column + "' already holds " + mappedBefore
                    + " and the import maps a column only once. Write the value to a new output column with a ROW"
                    + " function or custom code and map that column.", schemaId);
        }
        Map<Object, String> rejected;
        if (transformers.isEmpty() && context.fileProfiles().containsKey(column)) {
            rejected = previewValidationBO.rejectedValues(context.fileProfiles().get(column), schemaId, dataType);
        } else {
            List<Map<String, Object>> rows;
            try {
                rows = previewRunnerBO.run(context.configWith(transformers), context.keycloakId());
            } catch (RuntimeException e) {
                return new ConnectorValidationResultDTO("Running the transformer failed: " + e.getMessage(), schemaId);
            }
            // The preview runs on the real file, so the values the agent did not see are replaced
            Map<Object, String> real = previewValidationBO.rejectedValues(rows, column, schemaId, dataType);
            rejected = real == null ? null : dataPrivacy.rejected(real, context.fileProfiles());
        }

        if (rejected == null) {
            return new ConnectorValidationResultDTO("mappedColumn '" + column + "' does not exist after the transformation", schemaId);
        }
        if (rejected.isEmpty()) {
            rejected = rejectedFileValues(transformers, column);
        }
        if (emptiesEveryValue(column)) {
            return new ConnectorValidationResultDTO("Every value of '" + column + "' is empty after the "
                    + "transformation, although the source columns hold values. Check the parameters: a ROW "
                    + "function only reads the columns named in its inputMapping, a value starting with "
                    + AbstractManagedFunction.VALUE_PREFIX + " is a fixed text and no column, and the return key "
                    + "and the output column have to match.", schemaId);
        }
        if (rejected.isEmpty()) {
            // Categorical values are listed with what they become, for the others the emptied ones are counted
            String conversion = conversion(column);
            return new ConnectorValidationResultDTO("Valid" + (conversion.isEmpty() ? emptied(column) : conversion), schemaId, true);
        }
        String errors = rejected.entrySet().stream()
                .limit(MAX_REPORTED_ERRORS)
                .map(entry -> "'" + entry.getKey() + "': " + entry.getValue())
                .collect(Collectors.joining("\n"));
        return new ConnectorValidationResultDTO(rejected.size() + " rejected values of '" + column + "':\n" + errors, schemaId);
    }

    /**
     * Whether the transformers turn every value of the selected columns into an empty one, judged on the values of the
     * whole file: the preview holds only its first rows, which can all be missing markers.
     */
    private boolean emptiesEveryValue(String column) {
        return convertedValueRows != null && valueRows.stream().anyMatch(row -> selectedColumns.stream().anyMatch(source -> hasValue(row.get(source))))
                && convertedValueRows.stream().noneMatch(row -> hasValue(row.get(column)));
    }

    private boolean hasValue(Object value) {
        return value != null && !String.valueOf(value).isBlank()
                && !ConnectorValidationBO.isMissingPlaceholder(value, dataType == null ? null : dataType.getOptions());
    }

    private Map<Object, String> rejectedFileValues(List<ConnectorTransformerDTO> transformers, String column) {
        if (transformers.isEmpty() || valueRows.isEmpty() || transformers.stream().anyMatch(ConnectorTransformerDTO::isStoreTool)
                || readsSeveralColumns(transformers)) {
            return Map.of();
        }
        List<Map<String, Object>> rows;
        try {
            rows = transformationBO.applyBuiltInTransformations(transformers,
                    valueRows.stream().<Map<String, Object>>map(LinkedHashMap::new).toList(), context.patientIdColumn());
        } catch (RuntimeException e) {
            return Map.of("", "running on the values of the whole file failed: " + e.getMessage());
        }
        convertedValueRows = rows.size() == valueRows.size() ? rows : null;
        Map<Object, String> rejected = previewValidationBO.rejectedValues(rows, column, schemaNode.getId(), dataType);
        return rejected == null ? Map.of() : rejected;
    }


    private boolean readsSeveralColumns(List<ConnectorTransformerDTO> transformers) {
        return transformers.stream()
                .flatMap(transformer -> ConnectorHelper.transformerInputs(transformer).stream())
                .filter(selectedColumns::contains)
                .distinct()
                .count() > 1;
    }

    private String conversion(String mappedColumn) {
        boolean categorical = dataType != null && dataType.getOptions() != null && !dataType.getOptions().isEmpty();
        if (!categorical || convertedValueRows == null || selectedColumns.size() != 1) {
            return "";
        }
        String source = selectedColumns.getFirst();
        ColumnProfile profile = context.fileProfiles().get(source);
        if (profile == null) {
            return "";
        }
        Map<String, Integer> counts = Stream.concat(TransformerHelper.valueCounts(profile).stream(),
                        TransformerHelper.missingValues(profile).stream())
                .filter(entry -> entry.getKey() != null && entry.getValue() != null)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, Integer::sum));
        long blank = profile.missing() - TransformerHelper.missingValues(profile).stream().mapToLong(Map.Entry::getValue).sum();
        return ", the values of the file become: " + IntStream.range(0, valueRows.size())
                .limit(MAX_REPORTED_CONVERSIONS)
                .mapToObj(index -> {
                    Object value = valueRows.get(index).get(source);
                    long count = value == null ? blank : counts.getOrDefault(String.valueOf(value), 0);
                    return shown(value) + " x" + count + " -> " + shown(convertedValueRows.get(index).get(mappedColumn));
                })
                .collect(Collectors.joining(", "))
                + ". Check that every value becomes what it means.";
    }

    private String emptied(String column) {
        if (convertedValueRows == null) {
            return "";
        }
        List<Integer> withContent = IntStream.range(0, valueRows.size()).boxed()
                .filter(index -> selectedColumns.stream().anyMatch(source -> hasValue(valueRows.get(index).get(source))))
                .toList();
        List<String> emptied = withContent.stream()
                .filter(index -> !hasValue(convertedValueRows.get(index).get(column)))
                .map(index -> selectedColumns.stream().map(source -> shown(valueRows.get(index).get(source)))
                        .collect(Collectors.joining(" ")))
                .toList();
        return emptied.isEmpty() ? "" : ". " + emptied.size() + " of the " + withContent.size() + " values of the file "
                + "with content become empty, e.g. " + emptied.stream().limit(MAX_REPORTED_ERRORS).collect(Collectors.joining(", ")) + ".";
    }

    private static String shown(Object value) {
        return value == null || String.valueOf(value).isBlank() ? "(empty)" : "'" + value + "'";
    }

    private String json(ConnectorSelectedTransformer selected) {
        return objectMapper.valueToTree(selected).toString();
    }
}
