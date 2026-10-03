package bio.cosy.feddb.local.agent.etl.transform.agents;

import bio.cosy.feddb.local.agent.etl.transform.model.ConnectorSelectedTransformer;
import bio.cosy.feddb.local.api.importer.files.table.TableSample;
import bio.cosy.feddb.local.api.importer.validation.ConnectorValidationResultDTO;
import bio.cosy.feddb.local.api.schema.LocalSchemaNodeDTO;
import dev.langchain4j.agentic.agent.ErrorContext;
import dev.langchain4j.agentic.agent.ErrorRecoveryResult;
import dev.langchain4j.agentic.declarative.ErrorHandler;
import dev.langchain4j.agentic.declarative.SequenceAgent;

public interface TransformerWorkflow {

    int MAX_FAILED_ANSWERS = 3;

    @SequenceAgent(outputKey = "selectedTransformer", subAgents = {ColumnSelectAgent.class, IfColumnsSelected.class})
    ConnectorSelectedTransformer select(TableSample previewData, LocalSchemaNodeDTO schemaNode, String instructions);

    @ErrorHandler
    static ErrorRecoveryResult retryWithFeedback(ErrorContext errorContext) {
        if (!"selectTransformer".equals(errorContext.agentName())) {
            return ErrorRecoveryResult.throwException();
        }
        int failedAnswers = errorContext.agenticScope().readState("failedAnswers", 0) + 1;
        if (failedAnswers > MAX_FAILED_ANSWERS) {
            return ErrorRecoveryResult.throwException();
        }
        Throwable cause = errorContext.exception();
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        errorContext.agenticScope().writeState("failedAnswers", failedAnswers);
        errorContext.agenticScope().writeState("validation", new ConnectorValidationResultDTO(
                "Your previous answer could not be processed: " + cause.getMessage()
                        + ". Make sure every tool call and answer is complete, valid JSON."));
        return ErrorRecoveryResult.retry();
    }
}
