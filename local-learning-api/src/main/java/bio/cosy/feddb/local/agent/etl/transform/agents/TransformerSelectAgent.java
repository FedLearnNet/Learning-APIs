package bio.cosy.feddb.local.agent.etl.transform.agents;

import bio.cosy.feddb.local.agent.etl.transform.model.ConnectorSelectedTransformer;
import bio.cosy.feddb.local.agent.etl.transform.tools.TransformerListTool;
import bio.cosy.feddb.local.agent.etl.transform.tools.TransformerValidateTool;
import bio.cosy.feddb.local.api.importer.files.table.TableSample;
import bio.cosy.feddb.local.api.importer.validation.ConnectorValidationResultDTO;
import bio.cosy.feddb.local.api.schema.LocalSchemaNodeDTO;
import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.declarative.K;
import dev.langchain4j.agentic.declarative.TypedKey;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.ToolBox;

import java.util.List;

public interface TransformerSelectAgent {

    @UserMessage("""
            Your job is to select the best transformer to fill the schema node below from the selected columns.
            - If a column already holds valid values, set foundTransformer, set it as mappedColumn and leave selectedTransformer empty.
            - Otherwise pick the best fitting function of the transformer list tool and configure it.
              CELL functions rewrite their column in place, so mappedColumn is that column.
              ROW and PATIENT functions read inputMapping (parameter -> source column) and write returnMapping
              (return key -> output column); write to a new output column and use it as mappedColumn.
            - Check your selection with the validation tool before answering.
            - If no function fits, set foundTransformer to false and describe the needed function.

            Schema node: {schemaNode.name}
            {#if schemaNode.description}Description: {schemaNode.description}{/if}
            {#if schemaNode.dataType}
            Data type: {schemaNode.dataType.name} ({schemaNode.dataType.type})
            {#if schemaNode.dataType.allowedValues}Allowed values: {schemaNode.dataType.allowedValues}{/if}
            {#if schemaNode.dataType.validations}Validations: {#for v in schemaNode.dataType.validations}{v.name} {v.validator}; {/for}{/if}
            {/if}

            Selected columns:
            {#for row in previewData.rows}
            {#for column in selectedColumns}{column}={row.get(column)}{#if column_hasNext}, {/if}{/for}
            {/for}

            Result of the previous attempt: {validation.message}
            """)
    @Agent(description = "Selects and configures the transformer for a schema node", outputKey = "selectedTransformer")
    @ToolBox({TransformerListTool.class, TransformerValidateTool.class})
    ConnectorSelectedTransformer select(TableSample previewData,
                                        LocalSchemaNodeDTO schemaNode,
                                        List<String> selectedColumns,
                                        @K(PreviousValidation.class) ConnectorValidationResultDTO validation);

    class PreviousValidation implements TypedKey<ConnectorValidationResultDTO> {
        @Override
        public ConnectorValidationResultDTO defaultValue() {
            return new ConnectorValidationResultDTO("none, this is the first attempt");
        }

        @Override
        public String name() {
            return "validation";
        }
    }
}
