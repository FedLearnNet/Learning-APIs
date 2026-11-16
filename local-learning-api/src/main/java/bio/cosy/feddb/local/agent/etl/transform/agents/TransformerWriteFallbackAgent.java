package bio.cosy.feddb.local.agent.etl.transform.agents;

import bio.cosy.feddb.local.agent.etl.transform.model.ConnectorSelectedTransformer;
import bio.cosy.feddb.local.api.importer.files.table.TableSample;
import bio.cosy.feddb.local.api.importer.validation.ConnectorValidationResultDTO;
import bio.cosy.feddb.local.api.schema.LocalSchemaNodeDTO;
import dev.langchain4j.agentic.declarative.ActivationCondition;
import dev.langchain4j.agentic.declarative.ConditionalAgent;
import org.eclipse.microprofile.config.ConfigProvider;

import java.util.List;

public interface TransformerWriteFallbackAgent {

    @ConditionalAgent(outputKey = "selectedTransformer", subAgents = {TransformerWriteLoopAgent.class})
    ConnectorSelectedTransformer write(TableSample selectedData, LocalSchemaNodeDTO schemaNode,
                                       List<String> selectedColumns, String instructions, String fileValues);

    @ActivationCondition(TransformerWriteLoopAgent.class)
    static boolean nothingFits(ConnectorSelectedTransformer selectedTransformer, ConnectorValidationResultDTO validation) {
        return writerEnabled() && (selectedTransformer == null || !selectedTransformer.isFoundTransformer()
                || validation == null || !validation.isValid());
    }

    // Off in the evaluation setting without the writer (TransformerAgentConfig.writer)
    private static boolean writerEnabled() {
        return ConfigProvider.getConfig().getOptionalValue("flnet.connector.agent.writer", Boolean.class).orElse(true);
    }
}
