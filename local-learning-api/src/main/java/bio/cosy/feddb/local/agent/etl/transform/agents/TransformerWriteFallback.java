package bio.cosy.feddb.local.agent.etl.transform.agents;

import bio.cosy.feddb.local.agent.etl.transform.model.ConnectorSelectedTransformer;
import bio.cosy.feddb.local.api.importer.files.table.TableSample;
import bio.cosy.feddb.local.api.importer.validation.ConnectorValidationResultDTO;
import bio.cosy.feddb.local.api.schema.LocalSchemaNodeDTO;
import dev.langchain4j.agentic.declarative.ActivationCondition;
import dev.langchain4j.agentic.declarative.ConditionalAgent;

import java.util.List;

public interface TransformerWriteFallback {

    @ConditionalAgent(outputKey = "selectedTransformer", subAgents = {TransformerWriteLoop.class})
    ConnectorSelectedTransformer write(TableSample selectedData, LocalSchemaNodeDTO schemaNode,
                                       List<String> selectedColumns, String instructions);

    @ActivationCondition(TransformerWriteLoop.class)
    static boolean nothingFits(ConnectorSelectedTransformer selectedTransformer, ConnectorValidationResultDTO validation) {
        return selectedTransformer == null || !selectedTransformer.isFoundTransformer()
                || validation == null || !validation.isValid();
    }
}
