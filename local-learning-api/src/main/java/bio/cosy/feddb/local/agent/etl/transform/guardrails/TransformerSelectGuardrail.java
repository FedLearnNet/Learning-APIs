package bio.cosy.feddb.local.agent.etl.transform.guardrails;

import bio.cosy.feddb.local.agent.etl.transform.bot.LossReview;
import bio.cosy.feddb.local.agent.etl.transform.bot.LossReviewBot;
import bio.cosy.feddb.local.agent.etl.transform.model.ConnectorSelectedTransformer;
import bio.cosy.feddb.local.agent.etl.transform.tools.TransformerValidateTool;
import bio.cosy.feddb.local.api.schema.LocalSchemaNodeDTO;
import dev.langchain4j.guardrail.OutputGuardrailResult;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Map;

@ApplicationScoped
public class TransformerSelectGuardrail extends TransformerAnswerGuardrail {

    static final String LOSSES = "LOSSES";
    static final String CODES = "CODES";

    @Inject
    TransformerValidateTool validateTool;

    @Inject
    LossReviewBot lossReviewBot;

    @Override
    protected OutputGuardrailResult check(ConnectorSelectedTransformer answer, Map<String, Object> variables) {
        if (!answer.isFoundTransformer()) {
            return checkNotFound(answer, variables);
        }
        OutputGuardrailResult functions = checkFunctions(answer);
        if (!functions.isSuccess()) {
            return functions;
        }
        OutputGuardrailResult losses = checkLosses(answer, variables);
        return losses.isSuccess() ? checkGuessedCodes(answer) : losses;
    }

    // Shares cannot tell apart codes of about the same count, only a code book or the user can
    private OutputGuardrailResult checkGuessedCodes(ConnectorSelectedTransformer answer) {
        String guessed = validateTool.flagGuessedCodes(answer);
        if (guessed.isEmpty() || !hints.once(CODES)) {
            return success();
        }
        return feedback("Your chain is valid, but the source column holds codes without labels, and codes with about "
                + "the same count become different values, so their shares do not tell which code means what: " + guessed
                + ". Ask the user with askUser which code means what; if the user leaves it to you, keep the chain. "
                + "Validate again and answer.");
    }

    private OutputGuardrailResult checkLosses(ConnectorSelectedTransformer answer, Map<String, Object> variables) {
        String losses = validateTool.losses(answer);
        if (losses.isEmpty() || !agentConfig.reviewAgents() || !hints.once(LOSSES)) {
            return success();
        }
        LossReview review;
        try {
            review = lossReviewBot.review(variables.get("schemaNode") instanceof LocalSchemaNodeDTO node ? node : null,
                    (String) variables.get("fileValues"), losses);
        } catch (RuntimeException e) {
            Log.warnf(e, "Reviewing the losses '%s' failed, keeping the answer", losses);
            return success();
        }
        if (review == null || review.isKeep() || review.getReason() == null || review.getReason().isBlank()) {
            return success();
        }
        return feedback("Your chain is valid, but a review of what it loses (" + losses + ") disagrees: "
                + review.getReason() + " Change the chain, or if you still think the loss is right, ask the user with "
                + "askUser; if the user leaves it to you, keep the information. Validate again and answer.");
    }
}
