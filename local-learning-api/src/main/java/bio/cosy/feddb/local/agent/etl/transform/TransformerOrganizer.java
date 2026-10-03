package bio.cosy.feddb.local.agent.etl.transform;

import bio.cosy.feddb.local.agent.etl.transform.agents.TransformerWorkflow;
import bio.cosy.feddb.local.agent.etl.transform.model.ConnectorSelectedTransformer;
import bio.cosy.feddb.local.agent.etl.transform.tools.TransformerValidateTool;
import bio.cosy.feddb.local.api.importer.files.table.TableSample;
import bio.cosy.feddb.local.api.importer.validation.ConnectorValidationResultDTO;
import bio.cosy.feddb.local.api.schema.LocalSchemaNodeDTO;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;

@ApplicationScoped
public class TransformerOrganizer {

    @Inject
    TransformerWorkflow transformerWorkflow;

    @Inject
    TransformerValidateTool validateTool;

    @ActivateRequestContext
    public ConnectorSelectedTransformer selectTransformer(TableSample previewData,
                                                          LocalSchemaNodeDTO schemaNode,
                                                          String patientIdColumn) {
        validateTool.init(previewData, schemaNode, patientIdColumn);
        ConnectorSelectedTransformer selected;
        try {
            selected = transformerWorkflow.select(previewData, schemaNode);
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
}
