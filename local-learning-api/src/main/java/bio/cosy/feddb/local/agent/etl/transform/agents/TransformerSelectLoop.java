package bio.cosy.feddb.local.agent.etl.transform.agents;

import bio.cosy.feddb.local.agent.etl.transform.model.ConnectorSelectedTransformer;
import bio.cosy.feddb.local.agent.etl.transform.tools.TransformerValidateTool;
import bio.cosy.feddb.local.api.importer.files.table.TableSample;
import bio.cosy.feddb.local.api.importer.validation.ConnectorValidationResultDTO;
import bio.cosy.feddb.local.api.schema.LocalSchemaNodeDTO;
import dev.langchain4j.agentic.declarative.ExitCondition;
import dev.langchain4j.agentic.declarative.LoopAgent;

import java.util.List;

public interface TransformerSelectLoop {

    @LoopAgent(outputKey = "selectedTransformer", maxIterations = 5,
            subAgents = {TransformerSelectAgent.class, TransformerValidateTool.class})
    ConnectorSelectedTransformer select(TableSample selectedData, LocalSchemaNodeDTO schemaNode,
                                        List<String> selectedColumns, String instructions);

    @ExitCondition(testExitAtLoopEnd = true)
    static boolean done(ConnectorSelectedTransformer selectedTransformer, ConnectorValidationResultDTO validation) {
        return validation.isValid() || !selectedTransformer.isFoundTransformer();
    }

}
