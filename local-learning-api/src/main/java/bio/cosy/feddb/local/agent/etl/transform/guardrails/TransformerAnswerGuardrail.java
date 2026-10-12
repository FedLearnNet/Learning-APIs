package bio.cosy.feddb.local.agent.etl.transform.guardrails;

import bio.cosy.feddb.local.agent.etl.transform.bot.NotFoundReview;
import bio.cosy.feddb.local.agent.etl.transform.bot.NotFoundReviewBot;
import bio.cosy.feddb.local.agent.etl.transform.model.ConnectorSelectedTransformer;
import bio.cosy.feddb.local.agent.etl.transform.tools.TransformerListTool;
import bio.cosy.feddb.local.api.importer.transformer.ConnectorTransformerDTO;
import bio.cosy.feddb.local.api.schema.LocalSchemaNodeDTO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.guardrail.OutputGuardrail;
import dev.langchain4j.guardrail.OutputGuardrailRequest;
import dev.langchain4j.guardrail.OutputGuardrailResult;
import io.quarkus.logging.Log;
import jakarta.inject.Inject;

import java.util.List;
import java.util.Map;


public abstract class TransformerAnswerGuardrail implements OutputGuardrail {

    static final String ANSWER_ONLY = "Answer only with the corrected JSON object, without calling tools.";

    @Inject
    ObjectMapper objectMapper;

    @Inject
    TransformerListTool listTool;

    @Inject
    NotFoundReviewBot reviewBot;

    @Inject
    AnswerHints hints;

    @Override
    public OutputGuardrailResult validate(OutputGuardrailRequest request) {
        AiMessage message = request.responseFromLLM() == null ? null : request.responseFromLLM().aiMessage();
        String text = message == null ? null : message.text();
        if (text == null || text.isBlank()) {
            return reprompt("The answer was empty", "Your answer was empty. " + ANSWER_ONLY);
        }
        ConnectorSelectedTransformer answer = parse(text);
        if (answer == null) {
            return reprompt("The answer is no valid JSON", "Your answer is no valid JSON. " + ANSWER_ONLY);
        }
        Map<String, Object> variables = request.requestParams() == null || request.requestParams().variables() == null
                ? Map.of() : request.requestParams().variables();
        return check(answer, variables);
    }

    protected abstract OutputGuardrailResult check(ConnectorSelectedTransformer answer, Map<String, Object> variables);

    protected OutputGuardrailResult checkFunctions(ConnectorSelectedTransformer answer) {
        List<String> unknown = unknownTransformers(answer.getSelectedTransformers());
        if (unknown.isEmpty()) {
            return success();
        }
        String message = "Unknown functions " + unknown;
        return reprompt(message, message + ". Copy moduleName and methodName exactly from getAllDetail (built-in "
                + "functions: " + String.join(", ", listTool.builtInFunctionNames()) + "), getAllStoreDetailed or "
                + "getAllCodeDetailed. If none of them fits, set foundTransformer to false and describe the needed "
                + "function in neededTransformerFunction. " + ANSWER_ONLY);
    }

    protected List<String> unknownTransformers(List<ConnectorTransformerDTO> transformers) {
        return transformers.stream()
                .filter(transformer -> transformer.isStoreTool()
                        ? !listTool.resolveAppTransformer(transformer, null)
                        : !listTool.isBuiltInFunction(transformer))
                .map(transformer -> transformer.getModuleName() + "/" + transformer.getMethodName())
                .toList();
    }

    /** The review agent judges why nothing was found; every kind of feedback is given once per schema node. */
    protected OutputGuardrailResult checkNotFound(ConnectorSelectedTransformer answer, Map<String, Object> variables) {
        String reason = answer.getNeededTransformerFunction();
        if (answer.isFoundTransformer() || reason == null || reason.isBlank()) {
            return success();
        }
        NotFoundReview review;
        try {
            review = reviewBot.review(variables.get("schemaNode") instanceof LocalSchemaNodeDTO node ? node : null,
                    variables.get("selectedColumns"), (String) variables.get("fileValues"), reason);
        } catch (RuntimeException e) {
            Log.warnf(e, "Reviewing the answer '%s' failed, keeping it", reason);
            return success();
        }
        if (review == NotFoundReview.ASKS_USER && hints.once(review.name())) {
            return feedback("You wrote a question into neededTransformerFunction, but nobody reads it there. Ask it "
                    + "with askUser, or decide it with the rules of the task, and answer again.");
        }
        if (review == NotFoundReview.BUILT_IN && hints.once(review.name())) {
            return feedback("Translating the values of one column onto the allowed values needs no custom code: "
                    + "configure the built-in value mapping functions from getAllDetail for every value of the file, "
                    + "validate the chain and answer with it.");
        }
        return success();
    }

    protected OutputGuardrailResult feedback(String message) {
        return failure(message, new AnswerRejectedException(message));
    }

    protected OutputGuardrailResult rewritten(ConnectorSelectedTransformer answer) {
        return successWith(json(answer), answer);
    }

    private ConnectorSelectedTransformer parse(String text) {
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end < start) {
            return null;
        }
        try {
            return objectMapper.readValue(text.substring(start, end + 1), ConnectorSelectedTransformer.class);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private String json(ConnectorSelectedTransformer answer) {
        try {
            return objectMapper.writeValueAsString(answer);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("The answer cannot be written as JSON", e);
        }
    }
}
