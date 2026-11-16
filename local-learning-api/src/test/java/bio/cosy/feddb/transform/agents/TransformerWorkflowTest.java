package bio.cosy.feddb.transform.agents;

import bio.cosy.feddb.local.agent.etl.transform.agents.TransformerSelectAgent;
import bio.cosy.feddb.local.agent.etl.transform.agents.TransformerWorkflowAgent;
import bio.cosy.feddb.local.agent.etl.transform.agents.TransformerWriterAgent;
import bio.cosy.feddb.local.agent.etl.transform.guardrails.AnswerRejectedException;
import bio.cosy.feddb.local.agent.etl.transform.model.ConnectorSelectedTransformer;
import bio.cosy.feddb.local.api.importer.validation.ConnectorValidationResultDTO;
import dev.langchain4j.agentic.agent.AgentInvocationException;
import dev.langchain4j.agentic.agent.ErrorContext;
import dev.langchain4j.agentic.agent.ErrorRecoveryResult;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.exception.InvalidRequestException;
import dev.langchain4j.guardrail.OutputGuardrailException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TransformerWorkflowTest {

    private static final AgentInvocationException MALFORMED = new AgentInvocationException("Failed to invoke agent",
            new InvalidRequestException("error parsing tool call: raw='{\"mappedColumn\":\"on_dm1\",\"?\"}'"));

    private final Map<String, Object> state = new HashMap<>();
    private final AgenticScope scope = mock(AgenticScope.class);

    @BeforeEach
    void setUp() {
        when(scope.readState(anyString(), any())).thenAnswer(invocation ->
                state.getOrDefault(invocation.getArgument(0), invocation.getArgument(1)));
        doAnswer(invocation -> state.put(invocation.getArgument(0), invocation.getArgument(1)))
                .when(scope).writeState(anyString(), any(Object.class));
    }

    @Test
    void malformedAnswerIsRetriedWithFeedback() {
        ErrorRecoveryResult result = TransformerWorkflowAgent.retryWithFeedback(error(TransformerSelectAgent.NAME));

        assertEquals(ErrorRecoveryResult.Type.RETRY, result.type());
        ConnectorValidationResultDTO feedback = (ConnectorValidationResultDTO) state.get("validation");
        assertTrue(feedback.getMessage().contains("error parsing tool call"));
    }

    @Test
    void givesUpAfterThreeMalformedAnswers() {
        for (int attempt = 0; attempt < TransformerWorkflowAgent.MAX_FAILED_ANSWERS; attempt++) {
            assertEquals(ErrorRecoveryResult.Type.RETRY,
                    TransformerWorkflowAgent.retryWithFeedback(error(TransformerWriterAgent.NAME)).type());
        }

        assertEquals(ErrorRecoveryResult.Type.THROW_EXCEPTION,
                TransformerWorkflowAgent.retryWithFeedback(error(TransformerWriterAgent.NAME)).type());
    }

    @Test
    void aRejectedAnswerIsRetriedWithTheFeedbackOfTheGuardrail() {
        ErrorRecoveryResult result = TransformerWorkflowAgent.retryWithFeedback(rejected(TransformerSelectAgent.NAME));

        assertEquals(ErrorRecoveryResult.Type.RETRY, result.type());
        assertEquals("Ask it with askUser", ((ConnectorValidationResultDTO) state.get("validation")).getMessage());
    }

    @Test
    void rejectedAnswersEndInNotFoundSoTheNodeGoesOn() {
        for (int attempt = 0; attempt < TransformerWorkflowAgent.MAX_FAILED_ANSWERS; attempt++) {
            TransformerWorkflowAgent.retryWithFeedback(rejected(TransformerSelectAgent.NAME));
        }

        ErrorRecoveryResult result = TransformerWorkflowAgent.retryWithFeedback(rejected(TransformerSelectAgent.NAME));

        assertEquals(ErrorRecoveryResult.Type.RETURN_RESULT, result.type());
        assertFalse(((ConnectorSelectedTransformer) result.result()).isFoundTransformer());
    }

    @Test
    void failuresOfOtherAgentsAreNotRetried() {
        assertEquals(ErrorRecoveryResult.Type.THROW_EXCEPTION,
                TransformerWorkflowAgent.retryWithFeedback(error("validateSelected")).type());
    }

    private ErrorContext rejected(String agentName) {
        return new ErrorContext(agentName, scope, new AgentInvocationException("Failed to invoke agent",
                new OutputGuardrailException("rejected", new AnswerRejectedException("Ask it with askUser"))));
    }

    private ErrorContext error(String agentName) {
        return new ErrorContext(agentName, scope, MALFORMED);
    }
}
