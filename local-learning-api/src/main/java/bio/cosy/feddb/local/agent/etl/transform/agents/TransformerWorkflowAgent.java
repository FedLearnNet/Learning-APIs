package bio.cosy.feddb.local.agent.etl.transform.agents;

import bio.cosy.feddb.local.agent.etl.transform.guardrails.AnswerRejectedException;
import bio.cosy.feddb.local.agent.etl.transform.model.ConnectorSelectedTransformer;
import bio.cosy.feddb.local.api.importer.files.table.TableSample;
import bio.cosy.feddb.local.api.importer.validation.ConnectorValidationResultDTO;
import bio.cosy.feddb.local.api.schema.LocalSchemaNodeDTO;
import dev.langchain4j.agentic.agent.ErrorContext;
import dev.langchain4j.agentic.agent.ErrorRecoveryResult;
import dev.langchain4j.agentic.declarative.ErrorHandler;
import dev.langchain4j.agentic.declarative.SequenceAgent;
import dev.langchain4j.guardrail.OutputGuardrailException;

import java.util.List;

public interface TransformerWorkflowAgent {

    int MAX_FAILED_ANSWERS = 3;

    @SequenceAgent(outputKey = "selectedTransformer",
            subAgents = {TransformerSelectLoop.class, TransformerWriteFallbackAgent.class})
    ConnectorSelectedTransformer select(TableSample selectedData, LocalSchemaNodeDTO schemaNode,
                                        List<String> selectedColumns, String instructions, String fileValues);

    @ErrorHandler
    static ErrorRecoveryResult retryWithFeedback(ErrorContext errorContext) {
        String agentName = errorContext.agentName();
        if (!TransformerSelectAgent.NAME.equals(agentName) && !TransformerWriterAgent.NAME.equals(agentName)) {
            return ErrorRecoveryResult.throwException();
        }
        String failedAnswersKey = agentName + "FailedAnswers";
        int failedAnswers = errorContext.agenticScope().readState(failedAnswersKey, 0) + 1;
        Throwable rejected = cause(errorContext.exception(), AnswerRejectedException.class);
        Throwable guardrail = rejected != null ? rejected : cause(errorContext.exception(), OutputGuardrailException.class);
        if (failedAnswers > MAX_FAILED_ANSWERS) {
            return guardrail == null ? ErrorRecoveryResult.throwException()
                    : ErrorRecoveryResult.result(ConnectorSelectedTransformer.notFound("No usable answer: " + guardrail.getMessage()));
        }
        errorContext.agenticScope().writeState(failedAnswersKey, failedAnswers);
        String feedback = guardrail != null ? guardrail.getMessage()
                : "Your previous answer could not be processed: " + rootMessage(errorContext.exception())
                + ". Make sure every tool call and answer is complete, valid JSON.";
        errorContext.agenticScope().writeState("validation", new ConnectorValidationResultDTO(feedback));
        return ErrorRecoveryResult.retry();
    }

    private static Throwable cause(Throwable exception, Class<? extends Throwable> type) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (type.isInstance(cause)) {
                return cause;
            }
        }
        return null;
    }

    private static String rootMessage(Throwable exception) {
        Throwable cause = exception;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause.getMessage();
    }
}
