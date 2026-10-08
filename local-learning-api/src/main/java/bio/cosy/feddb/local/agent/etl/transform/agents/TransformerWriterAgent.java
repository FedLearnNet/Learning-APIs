package bio.cosy.feddb.local.agent.etl.transform.agents;

import bio.cosy.feddb.local.agent.etl.transform.model.ConnectorSelectedTransformer;
import bio.cosy.feddb.local.agent.etl.transform.model.PreviousValidation;
import bio.cosy.feddb.local.agent.etl.transform.tools.TransformerListTool;
import bio.cosy.feddb.local.agent.etl.transform.tools.TransformerUserTool;
import bio.cosy.feddb.local.agent.etl.transform.tools.TransformerWriterTool;
import bio.cosy.feddb.local.api.importer.files.table.TableSample;
import bio.cosy.feddb.local.api.importer.validation.ConnectorValidationResultDTO;
import bio.cosy.feddb.local.api.schema.LocalSchemaNodeDTO;
import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.declarative.K;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import io.quarkiverse.langchain4j.ToolBox;

import java.util.List;

public interface TransformerWriterAgent {

    String NAME = "writeTransformer";

    @UserMessage("""
            None of the built-in functions and none of the ETL tools from the store computes the schema node below,
            so your job is to write a custom transformer in Python. It is saved and reused for other data later.
            - First look at getAllCodeDetailed: if a saved custom code transformer fits, answer with it instead of
              writing a new one. If getSavedCode returns code of a previous attempt, continue from it.
            - Write only the body of the method transform; its signature depends on the mode:
              CELL transform(self, value) gets one value of one column and returns the new value, it replaces the column.
              ROW transform(self, row: dict) gets the input columns of one row by their exact names, e.g.
              row["Date of birth"], and returns the value.
              PATIENT transform(self, rows: list[dict]) gets all rows of one patient and returns a list with one dict
              per row holding the result under the key 'value'. Only use it when the value depends on other rows.
            - Use plain Python and its standard library (re, datetime, math, ...); os, sys, subprocess, socket,
              shutil, importlib, ctypes, open, eval, exec, compile and dunder attributes are blocked.
            - Handle every value of the rows: return None for a missing or unusable value instead of raising.
              Return values valid for the data type, e.g. exactly the allowed values or ISO dates.
            - Run checkCode after every change. It runs the code on the preview rows and on every value of the input
              columns in the whole file, and validates every result against the data type; fix the code with the
              failed samples until the check succeeds.
            - A column that already holds another schema node cannot be mapped again; then use ROW, which writes
              the result to a new column.
            - Then save it with saveCode, with a general name and description of what it does. Answer with the
              transformer saveCode returns as the only selectedTransformers entry, its mappedColumn and
              foundTransformer set.
            - If the meaning of the values is unclear (e.g. date format, unit, codes), ask the user.
            - If no code can compute the schema node from the columns, set foundTransformer to false and explain why.
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

            {#if selectedTransformer}{#if selectedTransformer.neededTransformerFunction}Needed function: {selectedTransformer.neededTransformerFunction}{/if}{/if}
            Result of the last attempt: {validation.message}
            """)
    @Agent(name = NAME, description = "Writes, checks and saves a custom Python transformer for a schema node", outputKey = "selectedTransformer")
    @ToolBox({TransformerListTool.class, TransformerWriterTool.class, TransformerUserTool.class})
    ConnectorSelectedTransformer write(TableSample selectedData,
                                       LocalSchemaNodeDTO schemaNode,
                                       List<String> selectedColumns,
                                       String instructions,
                                       String fileValues,
                                       ConnectorSelectedTransformer selectedTransformer,
                                       @K(PreviousValidation.class) @V("validation") ConnectorValidationResultDTO validation);
}
