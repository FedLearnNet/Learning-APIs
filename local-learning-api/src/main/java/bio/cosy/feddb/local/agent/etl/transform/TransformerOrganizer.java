package bio.cosy.feddb.local.agent.etl.transform;

import bio.cosy.feddb.local.agent.etl.transform.agents.TransformerWorkflow;
import bio.cosy.feddb.local.agent.etl.transform.model.ConnectorSelectedTransformer;
import bio.cosy.feddb.local.agent.etl.transform.tools.TransformerUserTool;
import bio.cosy.feddb.local.agent.etl.transform.tools.TransformerValidateTool;
import bio.cosy.feddb.local.api.importer.files.table.TableSample;
import bio.cosy.feddb.local.api.importer.validation.ConnectorValidationResultDTO;
import bio.cosy.feddb.local.api.schema.LocalSchemaNodeDTO;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;

import java.util.Comparator;
import java.util.Locale;
import java.util.function.UnaryOperator;

@ApplicationScoped
public class TransformerOrganizer {

    @Inject
    TransformerWorkflow transformerWorkflow;

    @Inject
    TransformerValidateTool validateTool;

    @Inject
    TransformerUserTool userTool;

    @ActivateRequestContext
    public ConnectorSelectedTransformer selectTransformer(TableSample previewData,
                                                          LocalSchemaNodeDTO schemaNode,
                                                          String patientIdColumn,
                                                          String instructions,
                                                          UnaryOperator<String> askUser) {
        validateTool.init(previewData, schemaNode, patientIdColumn);
        userTool.init(askUser);
        String hints = instructions == null ? "" : instructions;
        ConnectorSelectedTransformer direct = directMapping(previewData, schemaNode);
        if (direct != null) {
            ConnectorValidationResultDTO validation = validateTool.validate(direct);
            if (validation.isValid()) {
                return direct;
            }
            hints += "\nColumn '" + direct.getMappedColumn() + "' has the name of the schema node, "
                    + "but mapping it directly was rejected: " + validation.getMessage();
        }
        ConnectorSelectedTransformer selected;
        try {
            selected = transformerWorkflow.select(previewData, schemaNode, hints.strip());
        } catch (RuntimeException e) {
            Log.warnf(e, "Transformer selection for schema node %s failed", schemaNode.getName());
            selected = null;
        }
        if (selected == null) {
            selected = new ConnectorSelectedTransformer();
            selected.setNeededTransformerFunction("No source column holds the information of this schema node");
            return selected;
        }
        ConnectorValidationResultDTO validation = validateTool.validate(selected);
        if (selected.isFoundTransformer() && !validation.isValid()) {
            selected.setFoundTransformer(false);
            selected.setNeededTransformerFunction(validation.getMessage());
        }
        return selected;
    }

    private ConnectorSelectedTransformer directMapping(TableSample previewData, LocalSchemaNodeDTO schemaNode) {
        String name = normalize(schemaNode.getName());
        if (name.isEmpty()) {
            return null;
        }
        return previewData.getColumns().stream()
                .filter(column -> normalize(column).equals(name))
                .min(Comparator.comparing(column -> !column.equals(schemaNode.getName())))
                .map(column -> {
                    ConnectorSelectedTransformer direct = new ConnectorSelectedTransformer();
                    direct.setFoundTransformer(true);
                    direct.setMappedColumn(column);
                    return direct;
                })
                .orElse(null);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }
}
