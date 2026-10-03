package bio.cosy.feddb.local.agent.etl.transform.agents;

import bio.cosy.feddb.local.agent.etl.transform.model.ConnectorSelectedTransformer;
import bio.cosy.feddb.local.api.importer.files.table.TableSample;
import bio.cosy.feddb.local.api.schema.LocalSchemaNodeDTO;
import dev.langchain4j.agentic.declarative.SequenceAgent;

public interface TransformerWorkflow {

    @SequenceAgent(outputKey = "selectedTransformer", subAgents = {ColumnSelectAgent.class, IfColumnsSelected.class})
    ConnectorSelectedTransformer select(TableSample previewData, LocalSchemaNodeDTO schemaNode);
}
