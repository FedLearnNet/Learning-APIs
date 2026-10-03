package bio.cosy.feddb.local.agent.etl.transform.agents;

import bio.cosy.feddb.local.agent.etl.transform.model.ConnectorSelectedTransformer;
import bio.cosy.feddb.local.api.importer.files.table.TableSample;
import bio.cosy.feddb.local.api.schema.LocalSchemaNodeDTO;
import dev.langchain4j.agentic.declarative.ActivationCondition;
import dev.langchain4j.agentic.declarative.ConditionalAgent;

import java.util.List;

public interface IfColumnsSelected {

    @ConditionalAgent(outputKey = "selectedTransformer", subAgents = ValidationLoop.class)
    ConnectorSelectedTransformer select(TableSample previewData, LocalSchemaNodeDTO schemaNode, List<String> selectedColumns);

    @ActivationCondition(ValidationLoop.class)
    static boolean hasColumns(List<String> selectedColumns) {
        return !selectedColumns.isEmpty();
    }
}
