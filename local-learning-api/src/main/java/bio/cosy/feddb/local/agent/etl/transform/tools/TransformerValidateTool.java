package bio.cosy.feddb.local.agent.etl.transform.tools;

import bio.cosy.feddb.core.api.datamodler.datatype.DataTypeNodeDTO;
import bio.cosy.feddb.local.agent.etl.transform.helper.EtlAgentLog;
import bio.cosy.feddb.local.agent.etl.transform.helper.AgentDataPrivacy;
import bio.cosy.feddb.local.agent.etl.transform.model.ConnectorSelectedTransformer;
import bio.cosy.feddb.local.agent.etl.transform.model.TransformerRunContext;
import bio.cosy.feddb.local.api.importer.run.preview.ConnectorPreviewRunnerBO;
import bio.cosy.feddb.local.api.importer.transformer.ConnectorTransformationBO;
import bio.cosy.feddb.local.api.importer.transformer.ConnectorTransformerDTO;
import bio.cosy.feddb.local.api.importer.validation.ConnectorValidationResultDTO;
import bio.cosy.feddb.local.api.importer.validation.PreviewValidationBO;
import bio.cosy.feddb.local.api.schema.LocalSchemaNodeDTO;
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

@RequestScoped
public class TransformerValidateTool {

    private static final int MAX_REPORTED_ERRORS = 10;

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

    private TransformerRunContext context;
    private LocalSchemaNodeDTO schemaNode;
    private DataTypeNodeDTO dataType;
    private List<Map<String, Object>> valueRows;

    public void init(TransformerRunContext context, LocalSchemaNodeDTO schemaNode, DataTypeNodeDTO dataType,
                     List<Map<String, Object>> valueRows) {
        this.context = context;
        this.schemaNode = schemaNode;
        this.dataType = dataType;
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
        return Arc.container().select(TransformerValidateTool.class).get().validate(selectedTransformer);
    }

    public ConnectorValidationResultDTO validate(ConnectorSelectedTransformer selected) {
        ConnectorValidationResultDTO result = check(selected);
        context.step("%s %s", EtlAgentLog.describe(selected),
                result.isValid() ? "valid" : "rejected (" + EtlAgentLog.shorten(result.getMessage()) + ")");
        return result;
    }

    private ConnectorValidationResultDTO check(ConnectorSelectedTransformer selected) {
        Long schemaId = schemaNode.getId();
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
        if (rejected.isEmpty()) {
            return new ConnectorValidationResultDTO(schemaId);
        }
        String errors = rejected.entrySet().stream()
                .limit(MAX_REPORTED_ERRORS)
                .map(entry -> "'" + entry.getKey() + "': " + entry.getValue())
                .collect(Collectors.joining("\n"));
        return new ConnectorValidationResultDTO(rejected.size() + " rejected values of '" + column + "':\n" + errors, schemaId);
    }

    private Map<Object, String> rejectedFileValues(List<ConnectorTransformerDTO> transformers, String column) {
        if (transformers.isEmpty() || valueRows.isEmpty() || transformers.stream().anyMatch(ConnectorTransformerDTO::isStoreTool)) {
            return Map.of();
        }
        List<Map<String, Object>> rows;
        try {
            rows = transformationBO.applyBuiltInTransformations(transformers,
                    valueRows.stream().<Map<String, Object>>map(LinkedHashMap::new).toList(), context.patientIdColumn());
        } catch (RuntimeException e) {
            return Map.of("", "running on the values of the whole file failed: " + e.getMessage());
        }
        Map<Object, String> rejected = previewValidationBO.rejectedValues(rows, column, schemaNode.getId(), dataType);
        return rejected == null ? Map.of() : rejected;
    }
}
