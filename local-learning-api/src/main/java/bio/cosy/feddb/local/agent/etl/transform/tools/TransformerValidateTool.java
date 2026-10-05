package bio.cosy.feddb.local.agent.etl.transform.tools;

import bio.cosy.feddb.core.api.datamodler.datatype.DataTypeNodeDTO;
import bio.cosy.feddb.core.api.file.analytics.ColumnProfile;
import bio.cosy.feddb.local.agent.etl.transform.model.ConnectorSelectedTransformer;
import bio.cosy.feddb.local.api.importer.files.table.TableSample;
import bio.cosy.feddb.local.api.importer.transformer.ConnectorTransformerBO;
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

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RequestScoped
public class TransformerValidateTool {

    private static final int MAX_REPORTED_ERRORS = 10;

    @Inject
    ConnectorTransformerBO transformerBO;

    @Inject
    PreviewValidationBO previewValidationBO;

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

    @Tool("Validate transformers before answering: runs them in order on the preview data and checks the values of mappedColumn against the schema node. Pass an empty list for a direct mapping.")
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
        Long schemaId = schemaNode.getId();
        if (selected == null || !selected.isFoundTransformer()) {
            return new ConnectorValidationResultDTO("No transformer selected", schemaId);
        }
        List<ConnectorTransformerDTO> transformers = selected.getSelectedTransformers();
        List<ConnectorTransformerDTO> storeTools = transformers.stream().filter(TransformerListTool::isStoreTool).toList();
        if (!storeTools.isEmpty()) {
            return validateStoreTools(storeTools, schemaId);
        }

        List<Map<String, Object>> rows;
        try {
            rows = transformerBO.applyBuiltInTransformations(transformers, previewData.getRows(), patientIdColumn);
        } catch (RuntimeException e) {
            return new ConnectorValidationResultDTO("Running the transformer failed: " + e.getMessage(), schemaId);
        }

        String column = selected.getMappedColumn();
        Map<Object, String> rejected;
        if (transformers.isEmpty() && fileProfiles.containsKey(column)) {
            rejected = previewValidationBO.rejectedValues(fileProfiles.get(column), schemaId, dataType);
        } else {
            rejected = previewValidationBO.rejectedValues(rows, column, schemaId, dataType);
        }

        if (rejected == null) {
            return new ConnectorValidationResultDTO("mappedColumn '" + column + "' does not exist after the transformation", schemaId);
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

    private ConnectorValidationResultDTO validateStoreTools(List<ConnectorTransformerDTO> storeTools, Long schemaId) {
        if (!storeTools.stream().allMatch(listTool::resolveStoreTool)) {
            return new ConnectorValidationResultDTO("Unknown store tool. Copy appVersionId and appImage exactly from getAllStoreDetailed.", schemaId);
        }
        ConnectorValidationResultDTO result = new ConnectorValidationResultDTO(schemaId);
        result.setMessage("Store tool " + storeTools.getFirst().getMethodName()
                + " runs in a container, so the chain is validated when the connector runs");
        return result;
    }
}
