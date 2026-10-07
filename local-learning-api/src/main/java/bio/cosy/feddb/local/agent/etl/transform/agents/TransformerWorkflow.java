package bio.cosy.feddb.local.agent.etl.transform.agents;

import bio.cosy.feddb.local.agent.etl.transform.model.ConnectorSelectedTransformer;
import bio.cosy.feddb.local.api.importer.files.table.TableSample;
import bio.cosy.feddb.local.api.importer.validation.ConnectorValidationResultDTO;
import bio.cosy.feddb.local.api.schema.LocalSchemaNodeDTO;
import dev.langchain4j.agentic.agent.ErrorContext;
import dev.langchain4j.agentic.agent.ErrorRecoveryResult;
import dev.langchain4j.agentic.declarative.SequenceAgent;

import java.util.List;


public interface TransformerWorkflow {

    int MAX_FAILED_ANSWERS = 3;

    @SequenceAgent(outputKey = "selectedTransformer",
            subAgents = {TransformerSelectLoop.class, TransformerWriteFallback.class})
    ConnectorSelectedTransformer select(TableSample selectedData, LocalSchemaNodeDTO schemaNode,
                                        List<String> selectedColumns, String instructions);

    /** Retries the agent with feedback when its answer could not be processed, e.g. incomplete JSON. */
    static ErrorRecoveryResult retryWithFeedback(ErrorContext errorContext, String agentName) {
        if (!agentName.equals(errorContext.agentName())) {
            return ErrorRecoveryResult.throwException();
        }
        String failedAnswersKey = agentName + "FailedAnswers";
        int failedAnswers = errorContext.agenticScope().readState(failedAnswersKey, 0) + 1;
        if (failedAnswers > MAX_FAILED_ANSWERS) {
            return ErrorRecoveryResult.throwException();
        }
        Throwable cause = errorContext.exception();
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        errorContext.agenticScope().writeState(failedAnswersKey, failedAnswers);
        errorContext.agenticScope().writeState("validation", new ConnectorValidationResultDTO(
                "Your previous answer could not be processed: " + cause.getMessage()
                        + ". Make sure every tool call and answer is complete, valid JSON."));
        return ErrorRecoveryResult.retry();
    }
}
