package bio.cosy.feddb.transform.guardrails;

import bio.cosy.feddb.local.agent.etl.transform.bot.LossReview;
import bio.cosy.feddb.local.agent.etl.transform.bot.LossReviewBot;
import bio.cosy.feddb.local.agent.etl.transform.bot.NotFoundReview;
import bio.cosy.feddb.local.agent.etl.transform.config.TransformerAgentConfig;
import bio.cosy.feddb.local.agent.etl.transform.bot.NotFoundReviewBot;
import bio.cosy.feddb.local.agent.etl.transform.guardrails.AnswerHints;
import bio.cosy.feddb.local.agent.etl.transform.guardrails.AnswerRejectedException;
import bio.cosy.feddb.local.agent.etl.transform.guardrails.TransformerSelectGuardrail;
import bio.cosy.feddb.local.agent.etl.transform.guardrails.TransformerWriterGuardrail;
import bio.cosy.feddb.local.agent.etl.transform.model.ConnectorSelectedTransformer;
import bio.cosy.feddb.local.agent.etl.transform.model.SavedCodeTransformer;
import bio.cosy.feddb.local.agent.etl.transform.tools.TransformerListTool;
import bio.cosy.feddb.local.agent.etl.transform.tools.TransformerValidateTool;
import bio.cosy.feddb.local.agent.etl.transform.tools.TransformerWriterTool;
import bio.cosy.feddb.local.api.importer.transformer.ConnectorTransformerDTO;
import bio.cosy.feddb.local.api.schema.LocalSchemaNodeDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.guardrail.ChatExecutor;
import dev.langchain4j.guardrail.GuardrailRequestParams;
import dev.langchain4j.guardrail.OutputGuardrailRequest;
import dev.langchain4j.guardrail.OutputGuardrailResult;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransformerGuardrailTest {

    private static final String MAPPER = "Generic Value Mapper";
    private static final String REASON = "A custom transformer that maps the codes of 'sex' to the allowed values";
    private static final String FILE_VALUES = "sex: '1' x10, '2' x12";

    private final LocalSchemaNodeDTO schemaNode = new LocalSchemaNodeDTO();

    @Mock
    TransformerListTool listTool;

    @Mock
    TransformerWriterTool writerTool;

    @Mock
    NotFoundReviewBot reviewBot;

    @Mock
    TransformerValidateTool validateTool;

    @Mock
    LossReviewBot lossReviewBot;

    @Spy
    ObjectMapper objectMapper = new ObjectMapper();

    @Spy
    AnswerHints hints = new AnswerHints();

    @Mock
    TransformerAgentConfig agentConfig;

    @InjectMocks
    TransformerSelectGuardrail selectGuardrail;

    @InjectMocks
    TransformerWriterGuardrail writerGuardrail;

    @BeforeEach
    void setUp() {
        lenient().when(listTool.isBuiltInFunction(any())).thenAnswer(invocation ->
                MAPPER.equals(invocation.<ConnectorTransformerDTO>getArgument(0).getMethodName()));
        lenient().when(listTool.builtInFunctionNames()).thenReturn(List.of("Replace Value", MAPPER));
        lenient().when(validateTool.losses(any())).thenReturn("");
        lenient().when(agentConfig.reviewAgents()).thenReturn(true);
    }

    @Test
    void feedsBackALossTheReviewRejectsOnce() {
        when(validateTool.losses(any())).thenReturn("1 of the 4 values of the file with content become empty, e.g. 'Not measured'.");
        LossReview review = new LossReview();
        review.setReason("'Not measured' states a fact, it becomes 'None'.");
        when(lossReviewBot.review(eq(schemaNode), eq(FILE_VALUES), any())).thenReturn(review);

        OutputGuardrailResult first = selectGuardrail.validate(request(found(transformer(MAPPER))));
        OutputGuardrailResult second = selectGuardrail.validate(request(found(transformer(MAPPER))));

        assertFalse(first.isSuccess());
        String feedback = assertInstanceOf(AnswerRejectedException.class, first.getFirstFailureException()).getMessage();
        assertTrue(feedback.contains("it becomes 'None'") && feedback.contains("askUser"), feedback);
        assertTrue(second.isSuccess(), "the review is given once per schema node");
    }

    @Test
    void keepsALossTheReviewAccepts() {
        when(validateTool.losses(any())).thenReturn("Different values of the file become one: 'Ja', 'Yes' -> 'Yes'.");
        LossReview review = new LossReview();
        review.setKeep(true);
        when(lossReviewBot.review(any(), any(), any())).thenReturn(review);

        assertTrue(selectGuardrail.validate(request(found(transformer(MAPPER)))).isSuccess());
    }

    @Test
    void acceptsAKnownFunctionUnchanged() {
        OutputGuardrailResult result = selectGuardrail.validate(request(found(transformer(MAPPER))));

        assertTrue(result.isSuccess());
        assertNull(result.successfulResult());
    }

    @Test
    void repromptsAnUnknownFunctionWithTheExistingOnes() {
        OutputGuardrailResult result = selectGuardrail.validate(request(found(transformer("generic value mapper"))));

        assertTrue(result.isReprompt());
        String reprompt = result.getReprompt().orElseThrow();
        assertTrue(reprompt.contains("Unknown functions [custom/generic value mapper]"), reprompt);
        assertTrue(reprompt.contains(MAPPER), reprompt);
    }

    @Test
    void repromptsAnAnswerThatIsNoJson() {
        OutputGuardrailResult result = selectGuardrail.validate(request(AiMessage.from("I would use the value mapper.")));

        assertTrue(result.isReprompt());
    }

    @Test
    void theReviewAgentGetsThePromptOfTheAnswer() {
        when(reviewBot.review(eq(schemaNode), eq(List.of("sex")), eq(FILE_VALUES), eq(REASON))).thenReturn(NotFoundReview.OK);

        OutputGuardrailResult result = selectGuardrail.validate(request(ConnectorSelectedTransformer.notFound(REASON)));

        assertTrue(result.isSuccess());
    }

    @Test
    void sendsAValueMappingTheReviewAgentFoundBackOnce() {
        when(reviewBot.review(any(), any(), any(), any())).thenReturn(NotFoundReview.BUILT_IN);

        OutputGuardrailResult first = selectGuardrail.validate(request(ConnectorSelectedTransformer.notFound(REASON)));
        OutputGuardrailResult second = selectGuardrail.validate(request(ConnectorSelectedTransformer.notFound(REASON)));

        assertFalse(first.isSuccess());
        assertFalse(first.isRetry());
        assertInstanceOf(AnswerRejectedException.class, first.getFirstFailureException());
        assertTrue(second.isSuccess());
    }

    @Test
    void sendsAQuestionTheReviewAgentFoundBackToAskTheUser() {
        when(reviewBot.review(any(), any(), any(), any())).thenReturn(NotFoundReview.ASKS_USER);

        OutputGuardrailResult result = writerGuardrail.validate(request(ConnectorSelectedTransformer.notFound(REASON)));

        assertFalse(result.isSuccess());
        assertTrue(result.failures().getFirst().message().contains("askUser"));
    }

    @Test
    void keepsTheAnswerWhenTheReviewFails() {
        when(reviewBot.review(any(), any(), any(), any())).thenThrow(new IllegalStateException("LLM down"));

        OutputGuardrailResult result = selectGuardrail.validate(request(ConnectorSelectedTransformer.notFound(REASON)));

        assertTrue(result.isSuccess());
    }

    @Test
    void takesTheSavedCodeInsteadOfWhatTheWriterRepeats() {
        ConnectorTransformerDTO saved = transformer("Parse codes");
        saved.setModuleName("Custom code");
        when(writerTool.savedAnswer()).thenReturn(new SavedCodeTransformer(true, "Saved", saved, "Readmitted"));

        OutputGuardrailResult result = writerGuardrail.validate(request(found(transformer("ReadmitMapper"))));

        ConnectorSelectedTransformer answer = assertInstanceOf(ConnectorSelectedTransformer.class, result.successfulResult());
        assertEquals("Readmitted", answer.getMappedColumn());
        assertEquals("Parse codes", answer.getSelectedTransformers().getFirst().getMethodName());
    }

    @Test
    void stopsTheWriterWhenNoCodeCanRun() {
        when(writerTool.unavailable()).thenReturn("The code runner could not be started: no docker");

        OutputGuardrailResult result = writerGuardrail.validate(request(found(transformer("transform"))));

        ConnectorSelectedTransformer answer = assertInstanceOf(ConnectorSelectedTransformer.class, result.successfulResult());
        assertFalse(answer.isFoundTransformer());
        assertTrue(answer.getNeededTransformerFunction().contains("no docker"));
        verify(reviewBot, never()).review(any(), any(), any(), any());
    }

    @Test
    void keepsABuiltInAnswerOfTheWriterWhenNoCodeCanRun() {
        lenient().when(writerTool.unavailable()).thenReturn("The code runner could not be started: no docker");

        OutputGuardrailResult result = writerGuardrail.validate(request(found(transformer(MAPPER))));

        assertTrue(result.isSuccess());
        assertNull(result.successfulResult());
    }

    @Test
    void sendsAnUnsavedTransformerOfTheWriterBackToSaveIt() {
        OutputGuardrailResult result = writerGuardrail.validate(request(found(transformer("transform"))));

        assertFalse(result.isSuccess());
        assertInstanceOf(AnswerRejectedException.class, result.getFirstFailureException());
    }

    private OutputGuardrailRequest request(ConnectorSelectedTransformer answer) {
        try {
            return request(AiMessage.from(objectMapper.writeValueAsString(answer)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private OutputGuardrailRequest request(AiMessage message) {
        return OutputGuardrailRequest.builder()
                .responseFromLLM(ChatResponse.builder().aiMessage(message).build())
                .chatExecutor(mock(ChatExecutor.class))
                .requestParams(GuardrailRequestParams.builder()
                        .userMessageTemplate("")
                        .variables(Map.of("schemaNode", schemaNode, "selectedColumns", List.of("sex"),
                                "fileValues", FILE_VALUES))
                        .build())
                .build();
    }

    private static ConnectorSelectedTransformer found(ConnectorTransformerDTO transformer) {
        return ConnectorSelectedTransformer.mapping("sex", List.of(transformer));
    }

    private static ConnectorTransformerDTO transformer(String method) {
        ConnectorTransformerDTO transformer = new ConnectorTransformerDTO();
        transformer.setModuleName("custom");
        transformer.setMethodName(method);
        transformer.setColumn("sex");
        transformer.setInputMapping(Map.of("map", "[VALUE]{\"1\":\"Male\",\"2\":\"Female\"}"));
        return transformer;
    }
}
