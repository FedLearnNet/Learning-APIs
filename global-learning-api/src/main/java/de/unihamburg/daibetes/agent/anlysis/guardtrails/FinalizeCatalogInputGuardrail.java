package de.unihamburg.daibetes.agent.anlysis.guardtrails;

import de.unihamburg.daibetes.agent.anlysis.PlanState;
import de.unihamburg.daibetes.agent.anlysis.tools.PlanNextStepDecision;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.guardrail.InputGuardrail;
import dev.langchain4j.guardrail.InputGuardrailRequest;
import dev.langchain4j.guardrail.InputGuardrailResult;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

@ApplicationScoped
public class FinalizeCatalogInputGuardrail implements InputGuardrail {

    private static final Pattern HTML_ACTION_LINK_PATTERN = Pattern.compile(
            "<a\\s+class=\"app-action\"\\s+data-kind=\"(APP|MODEL|FILE)\"\\s+data-action=\"(DETAIL|ADD_TO_WORKFLOW)\"\\s+data-id=\"([^\"]+)\"\\s*>.*?</a>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );

    private static final Pattern SUSPICIOUS_TOKEN_PATTERN = Pattern.compile(
            "\\b(APP_ID|MODEL_ID|FILE_ID|STORE_ID|ITEM_ID|decisiontree|datamining|unsupervised_clustering|listUsableStore)\\b",
            Pattern.CASE_INSENSITIVE
    );

    @Override
    public InputGuardrailResult validate(InputGuardrailRequest request) {
        UserMessage userMessage = request.userMessage();
        String originalUserText = userMessage == null ? "" : userMessage.singleText();

        Map<String, Object> variables = request.requestParams() == null
                ? Map.of()
                : request.requestParams().variables();

        Object stateObj = variables.get("state");
        if (!(stateObj instanceof PlanState state)) {
            return success();
        }

        List<String> warnings = new ArrayList<>();

        inspectTools(state.getTools(), warnings);
        inspectFileAnalyzeMap(state.getFileAnalyzeMap(), warnings);
        inspectNextStepDecision(state.getNextStepDecision(), warnings);

        if (warnings.isEmpty()) {
            return success();
        }

        String rewrittenMessage =
                originalUserText
                        + "\n\n[VALIDATION_FEEDBACK]\n"
                        + "Some catalog or tool references gathered earlier appear unvalidated, synthetic, or inconsistent.\n"
                        + "Treat them as untrusted unless they are backed by a validated catalog item with a stable ID.\n"
                        + "Do not emit action links for suspicious items.\n"
                        + "Prefer generic capability descriptions when validation is missing.\n"
                        + "Detected issues: "
                        + String.join("; ", warnings);

        return successWith(rewrittenMessage);
    }

    private void inspectTools(List<String> tools, List<String> warnings) {
        if (tools == null || tools.isEmpty()) {
            return;
        }

        for (String toolText : tools) {
            if (toolText == null || toolText.isBlank()) {
                continue;
            }

            if (SUSPICIOUS_TOKEN_PATTERN.matcher(toolText).find()) {
                warnings.add("suspicious catalog or tool token found in tools");
            }

            if (toolText.contains("app-action") && !HTML_ACTION_LINK_PATTERN.matcher(toolText).find()) {
                warnings.add("malformed action link found in tools");
            }

            if (containsSyntheticFamilyClaim(toolText)) {
                warnings.add("possible synthetic app/model/family claim in tools");
            }
        }
    }

    private void inspectFileAnalyzeMap(Map<Long, String> fileAnalyzeMap, List<String> warnings) {
        if (fileAnalyzeMap == null || fileAnalyzeMap.isEmpty()) {
            return;
        }

        for (String value : fileAnalyzeMap.values()) {
            if (value == null || value.isBlank()) {
                continue;
            }

            if (SUSPICIOUS_TOKEN_PATTERN.matcher(value).find()) {
                warnings.add("suspicious catalog or tool token found in file analysis results");
            }

            if (containsSyntheticFamilyClaim(value)) {
                warnings.add("possible synthetic app/model/family claim in file analysis results");
            }
        }
    }

    private void inspectNextStepDecision(PlanNextStepDecision decision, List<String> warnings) {
        if (decision == null || decision.getStoreId() == null) {
            return;
        }

        if (decision.getKind() == null || decision.getKind().isBlank()) {
            warnings.add("next step decision has storeId but missing kind");
            return;
        }

        String kind = decision.getKind().trim().toLowerCase(Locale.ROOT);
        if (!kind.equals("app") && !kind.equals("model")) {
            warnings.add("next step decision has invalid kind");
        }
    }

    private boolean containsSyntheticFamilyClaim(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        return lower.contains("app family")
                || lower.contains("tool family")
                || lower.contains("model family")
                || lower.contains("cluster family")
                || lower.contains("decision tree family")
                || lower.contains("data mining family");
    }
}
