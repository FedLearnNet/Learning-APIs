package bio.cosy.feddb.local.agent.etl.transform.agents;

import bio.cosy.feddb.local.agent.etl.transform.tools.TransformerListTool;
import bio.cosy.feddb.local.agent.etl.transform.tools.TransformerUserTool;
import bio.cosy.feddb.local.api.importer.files.table.TableSample;
import bio.cosy.feddb.local.api.schema.LocalSchemaNodeDTO;
import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.ToolBox;

import java.util.List;

public interface ColumnSelectAgent {

    @UserMessage("""
            Your job is to select the columns which are needed to calculate the value of the schema node below.
            Pick every column the value depends on (e.g. both dates for a duration), but no column that only looks similar.
            Return the exact column names, or an empty list if no column holds the information.
            The tool lists the available transformer functions, in case this helps to judge which inputs are needed.
            If several columns fit equally well, ask the user.
            {#if instructions}Instructions of the user: {instructions}{/if}

            Schema node: {schemaNode.name}
            {#if schemaNode.description}Description: {schemaNode.description}{/if}
            {#if schemaNode.dataType}Data type: {schemaNode.dataType.name} ({schemaNode.dataType.type}){/if}

            Columns: {previewData.columns}
            Rows:
            {#for row in previewData.rows}
            {row}
            {/for}
            """)
    @Agent(description = "Selects the columns needed for a schema node", outputKey = "selectedColumns")
    @ToolBox({TransformerListTool.class, TransformerUserTool.class})
    List<String> select(TableSample previewData, LocalSchemaNodeDTO schemaNode, String instructions);
}
