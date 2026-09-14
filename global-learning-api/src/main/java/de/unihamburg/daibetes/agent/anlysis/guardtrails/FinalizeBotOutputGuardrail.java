package de.unihamburg.daibetes.agent.anlysis.guardtrails;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.guardrail.OutputGuardrail;
import dev.langchain4j.guardrail.OutputGuardrailResult;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@ApplicationScoped
public class FinalizeBotOutputGuardrail implements OutputGuardrail {

    private static final Pattern ACTION_LINK_PATTERN = Pattern.compile(
            "<a\\s+class=\"app-action\"\\s+data-kind=\"([A-Z]+)\"\\s+data-action=\"([A-Z_]+)\"\\s+data-id=\"([^\"]+)\"\\s*>(.*?)</a>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );

    private static final Set<String> ALLOWED_KINDS = Set.of("APP", "MODEL", "FILE");
    private static final Set<String> ALLOWED_ACTIONS = Set.of("DETAIL", "ADD_TO_WORKFLOW");
    private static final Set<String> FORBIDDEN_PLACEHOLDER_IDS = Set.of(
            "APP_ID", "MODEL_ID", "FILE_ID", "ID", "STORE_ID", "ITEM_ID"
    );

    private static final Pattern SUSPICIOUS_ID_PATTERN = Pattern.compile(
            "^(app|model|file)?[_-]?(id|placeholder|example)$",
            Pattern.CASE_INSENSITIVE
    );

    @Override
    public OutputGuardrailResult validate(AiMessage responseFromLLM) {
        String text = responseFromLLM == null ? null : responseFromLLM.text();

        if (text == null || text.isBlank()) {
            return retry("The final answer was empty. Return a concise grounded Markdown answer.");
        }

        if (containsForbiddenPlaceholderId(text)) {
            return retry(
                    "Do not use placeholder or synthetic IDs such as APP_ID, MODEL_ID, FILE_ID, STORE_ID, or ITEM_ID. " +
                            "If no validated catalog item exists, describe the capability generically and do not emit an action link."
            );
        }

        if (containsMalformedActionLink(text)) {
            return retry(
                    "Action links must follow the exact format " +
                            "<a class=\"app-action\" data-kind=\"APP|MODEL|FILE\" data-action=\"DETAIL|ADD_TO_WORKFLOW\" data-id=\"VALIDATED_ID\">NAME</a>."
            );
        }

        Matcher matcher = ACTION_LINK_PATTERN.matcher(text);
        while (matcher.find()) {
            String kind = normalize(matcher.group(1));
            String action = normalize(matcher.group(2));
            String id = matcher.group(3) == null ? "" : matcher.group(3).trim();
            String label = matcher.group(4) == null ? "" : matcher.group(4).trim();

            if (!ALLOWED_KINDS.contains(kind)) {
                return retry("Invalid data-kind in action link. Allowed values are APP, MODEL, FILE.");
            }

            if (!ALLOWED_ACTIONS.contains(action)) {
                return retry("Invalid data-action in action link. Allowed values are DETAIL or ADD_TO_WORKFLOW.");
            }

            if (id.isBlank()) {
                return retry("Every action link must contain a non-empty validated data-id.");
            }

            if (FORBIDDEN_PLACEHOLDER_IDS.contains(id.toUpperCase(Locale.ROOT)) || SUSPICIOUS_ID_PATTERN.matcher(id).matches()) {
                return retry("Do not emit placeholder or synthetic catalog IDs in action links.");
            }

            if (label.isBlank()) {
                return retry("Every action link must contain a visible item name between the opening and closing tags.");
            }
        }

        return success();
    }

    private static boolean containsForbiddenPlaceholderId(String text) {
        String upper = text.toUpperCase(Locale.ROOT);
        for (String forbidden : FORBIDDEN_PLACEHOLDER_IDS) {
            if (upper.contains(forbidden)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsMalformedActionLink(String text) {
        if (!text.contains("app-action")) {
            return false;
        }

        Matcher matcher = ACTION_LINK_PATTERN.matcher(text);
        int validMatches = 0;
        while (matcher.find()) {
            validMatches++;
        }

        long rawAppActionCount = countOccurrencesIgnoreCase(text, "app-action");
        return rawAppActionCount > 0 && validMatches == 0;
    }

    private static long countOccurrencesIgnoreCase(String text, String needle) {
        String lowerText = text.toLowerCase(Locale.ROOT);
        String lowerNeedle = needle.toLowerCase(Locale.ROOT);

        long count = 0;
        int idx = 0;
        while ((idx = lowerText.indexOf(lowerNeedle, idx)) != -1) {
            count++;
            idx += lowerNeedle.length();
        }
        return count;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }
}
