package bio.cosy.feddb.local.agent.etl.transform.agents;

import bio.cosy.feddb.local.agent.etl.transform.model.ConnectorSelectedTransformer;
import bio.cosy.feddb.local.agent.etl.transform.model.PreviousValidation;
import bio.cosy.feddb.local.agent.etl.transform.tools.TransformerListTool;
import bio.cosy.feddb.local.agent.etl.transform.tools.TransformerUserTool;
import bio.cosy.feddb.local.agent.etl.transform.tools.TransformerValidateTool;
import bio.cosy.feddb.local.api.importer.files.table.TableSample;
import bio.cosy.feddb.local.api.importer.validation.ConnectorValidationResultDTO;
import bio.cosy.feddb.local.api.schema.LocalSchemaNodeDTO;
import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.declarative.K;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import io.quarkiverse.langchain4j.ToolBox;

import java.util.List;

public interface TransformerSelectAgent {

    @UserMessage("""
            Your job is to select the transformers that compute the schema node below from the selected columns.
            - If one column already holds valid values (validation tool with an empty transformer list), answer with
              foundTransformer set, that column as mappedColumn and no selectedTransformers.
            - Otherwise pick the best fitting functions of the transformer list tool and configure them. Use a chain of
              several functions in execution order when one is not enough, e.g. first merge or convert, then map values.
              A single wrong value (e.g. the missing-value placeholder '?') is fixed with the Replace Value function
              (search = the value, replace = null or the allowed value), several with the Generic Value Mapper.
              CELL functions rewrite their column in place, so mappedColumn is that column.
              ROW and PATIENT functions read inputMapping (parameter -> source column) and write returnMapping
              (return key -> output column); write to a new output column and use it as mappedColumn.
            - Custom code transformers saved earlier (getAllCodeDetailed) are configured like functions: moduleName
              'Custom code', methodName and appImage copied exactly, column for CELL, inputMapping (input key ->
              source column) and returnMapping (return key -> new output column) for ROW and PATIENT.
            - Only if neither a built-in function nor a custom code transformer fits, use a store tool from
              getAllStoreDetailed: moduleName 'tools', methodName the tool name, appVersionId and appImage copied exactly, column the comma separated source
              columns for the tool, returnMapping from a tool output column to a new column, hyperparams keyed by the
              listed parameter names. Validating a store tool starts its container, which takes a while, so only
              validate a store tool chain once it is complete.
            - Check the whole chain with the validation tool before answering.
            - If no function fits, set foundTransformer to false and describe the needed function precisely (input
              columns, output and how the values are converted), a custom transformer is written from it.
            - If you are unsure which of several options the user wants, ask the user; let the user select columns
              with selectColumns instead of asking for column names.
            {#if instructions}Instructions of the user: {instructions}{/if}
            
            Schema node: {schemaNode.name}
            {#if schemaNode.description}Description: {schemaNode.description}{/if}
            {#if schemaNode.dataType}
            Data type: {schemaNode.dataType.name} ({schemaNode.dataType.type})
            {#if schemaNode.dataType.allowedValues}Allowed values: {schemaNode.dataType.allowedValues}{/if}
            {#if schemaNode.dataType.validations}Validations: {#for v in schemaNode.dataType.validations}{v.name} {v.validator}; {/for}{/if}
            {/if}
            
            Selected columns: {selectedColumns}
            Rows:
            {#for row in selectedData.rows}
            {#if row_index < 15}{row}{/if}
            {/for}
            
            Result of the previous attempt: {validation.message}
            """)
    @Agent(name = "selectTransformer", description = "Selects and configures the transformer for a schema node", outputKey = "selectedTransformer")
    @ToolBox({TransformerListTool.class, TransformerValidateTool.class, TransformerUserTool.class})
    ConnectorSelectedTransformer select(TableSample selectedData,
                                        LocalSchemaNodeDTO schemaNode,
                                        List<String> selectedColumns,
                                        String instructions,
                                        @K(PreviousValidation.class) @V("validation") ConnectorValidationResultDTO validation);
}
