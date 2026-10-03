package bio.cosy.feddb.local.agent.etl.transform.tools;

import bio.cosy.feddb.local.agent.etl.transform.model.ConnectorSelectedTransformer;
import bio.cosy.feddb.local.api.importer.files.table.TableSample;
import bio.cosy.feddb.local.api.importer.transformer.ConnectorTransformerBO;
import bio.cosy.feddb.local.api.importer.validation.ConnectorValidationBO;
import bio.cosy.feddb.local.api.importer.validation.ConnectorValidationResultDTO;
import bio.cosy.feddb.local.api.schema.LocalSchemaNodeDTO;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agentic.Agent;
import io.quarkus.arc.Arc;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@RequestScoped
public class TransformerValidateTool {

    private static final int MAX_VALIDATED_VALUES = 50;

    @Inject
    ConnectorTransformerBO transformerBO;

    @Inject
    ConnectorValidationBO validationBO;

    private TableSample previewData;
    private LocalSchemaNodeDTO schemaNode;
    private String patientIdColumn;

    public void init(TableSample previewData, LocalSchemaNodeDTO schemaNode, String patientIdColumn) {
        this.previewData = previewData;
        this.schemaNode = schemaNode;
        this.patientIdColumn = patientIdColumn;
    }

    @Tool("Validate a selected transformer: runs it on the preview data and checks the values of mappedColumn against the schema node")
    public ConnectorValidationResultDTO validate(ConnectorSelectedTransformer selected) {
        Long schemaId = schemaNode.getId();
        if (selected == null || !selected.isFoundTransformer()) {
            return new ConnectorValidationResultDTO("No transformer selected", schemaId);
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
        List<String> errors = rows.stream()
                .map(row -> row.get(column))
                .distinct()
                .limit(MAX_VALIDATED_VALUES)
                .map(value -> {
                    ConnectorValidationResultDTO result = validationBO.validate(value, schemaId);
                    return result.isValid() ? null : "'" + value + "': " + result.getMessage();
                })
                .filter(Objects::nonNull)
                .toList();
        return errors.isEmpty()
                ? new ConnectorValidationResultDTO(schemaId)
                : new ConnectorValidationResultDTO("Rejected values of '" + column + "':\n" + String.join("\n", errors), schemaId);
    }

    @Agent(description = "Validates the selected transformer on the preview data", outputKey = "validation")
    public static ConnectorValidationResultDTO validateSelected(ConnectorSelectedTransformer selectedTransformer) {
        return Arc.container().select(TransformerValidateTool.class).get().validate(selectedTransformer);
    }

    private List<Map<String, Object>> transform(ConnectorSelectedTransformer selected) {
        List<Map<String, Object>> rows = previewData.toTableData().getRows().stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(ArrayList::new));
        if (selected.getSelectedTransformer() == null) {
            return rows;
        }
        return transformerBO.applyBuiltInTransformation(selected.getSelectedTransformer(), rows, patientIdColumn);
    }
}
