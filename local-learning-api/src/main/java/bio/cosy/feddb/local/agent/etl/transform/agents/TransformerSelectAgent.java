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

    String NAME = "selectTransformer";

    @UserMessage("""
            Your job is to select the transformers that compute the schema node below from the selected columns.
            - If one column already holds valid values (validation tool with an empty transformer list), answer with
              foundTransformer set, that column as mappedColumn and no selectedTransformers.
            - Otherwise configure existing transformers, strictly in this order; go to the next level only when nothing
              of the level before fits:
              1. Built-in functions (getAllDetail). Use a chain of several functions in execution order when one is
                 not enough, e.g. first merge or convert, then map values. A single wrong value (e.g. the
                 missing-value placeholder '?') is fixed with the Replace Value function (search = the value,
                 replace = null or the allowed value), several with the Generic Value Mapper. A cell holding several
                 values joined by a separator is split first (e.g. Split Column), then its parts are mapped.
              2. ETL tools from the store (getAllStoreDetailed): moduleName 'tools', methodName the tool name,
                 appVersionId and appImage copied exactly, column the comma separated source columns for the tool,
                 returnMapping from a tool output column to a new column, hyperparams keyed by the listed parameter
                 names. Validating a store tool starts its container, which takes a while, so only validate a store
                 tool chain once it is complete.
              3. Custom code transformers written and saved earlier (getAllCodeDetailed), configured like functions:
                 moduleName 'Custom code', methodName and appImage copied exactly, column for CELL, inputMapping
                 (input key -> source column) and returnMapping (return key -> new output column) for ROW and PATIENT.
              Map every value of the whole file listed below, not only the values of the rows.
              Codes (e.g. R1, 2) are mapped to the allowed values. When the description gives the share of each value,
              compare it with the counts of the file values below: a code with about the same share is that value, and
              a code about as frequent as the empty rows of the description stands for no value and becomes empty
              (null), never an allowed value. If codes are too close in share to tell apart, ask the user what they
              mean. Without shares, numeric codes follow the order of the meaning: 0 is the absent value (no, none, not
              measured), never an active one like a change or a result, and the higher codes rise from there (e.g. a
              lower, an equal, a higher state; normal before abnormal, mild before severe). A column holding a single
              code holds the absent value. A negative code
              like -1 is a missing value and becomes empty (null).
              A missing-value placeholder the description lists as a value of its own (e.g. '?') is kept: missing
              values of the file become that placeholder, not empty.
              CELL transformers rewrite their column in place, so mappedColumn is that column. A column that already
              holds another schema node cannot be mapped again; then write the result to a new output column.
              ROW and PATIENT transformers read inputMapping (parameter -> source column) and write returnMapping
              (return key -> output column); write to a new output column and use it as mappedColumn.
            - Check the whole chain with the validation tool before answering.
            - If none of the three levels fits, set foundTransformer to false and describe the needed function
              precisely (input columns, output and how the values are converted); only then new code is written.
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
            
            {#if fileValues}Values of the selected columns in the whole file, the rows above are only the first ones:
            {fileValues}
            {/if}

            Result of the previous attempt: {validation.message}
            """)
    @Agent(name = NAME, description = "Selects and configures the transformer for a schema node", outputKey = "selectedTransformer")
    @ToolBox({TransformerListTool.class, TransformerValidateTool.class, TransformerUserTool.class})
    ConnectorSelectedTransformer select(TableSample selectedData,
                                        LocalSchemaNodeDTO schemaNode,
                                        List<String> selectedColumns,
                                        String instructions,
                                        String fileValues,
                                        @K(PreviousValidation.class) @V("validation") ConnectorValidationResultDTO validation);
}
