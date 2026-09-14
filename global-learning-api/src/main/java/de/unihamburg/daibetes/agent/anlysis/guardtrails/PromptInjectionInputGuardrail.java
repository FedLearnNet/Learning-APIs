package de.unihamburg.daibetes.agent.anlysis.guardtrails;

import de.unihamburg.daibetes.config.FLNetConfig;
import de.unihamburg.daibetes.agent.anlysis.bots.PromptInjectionDetectionBot;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.guardrail.InputGuardrail;
import dev.langchain4j.guardrail.InputGuardrailResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;
import java.util.regex.Pattern;

@ApplicationScoped
public class PromptInjectionInputGuardrail implements InputGuardrail {

    @Inject
    PromptInjectionDetectionBot service;

    @Inject
    FLNetConfig config;

    private static final List<Pattern> BLOCKLIST = List.of(
            // Prompt injection patterns
            Pattern.compile("(?i)ignore (all )?previous instructions"),
            Pattern.compile("(?i)disregard .* system message"),
            Pattern.compile("(?i)reveal (the )?(system|developer) prompt"),
            Pattern.compile("(?i)you are now (my|the) system"),
            Pattern.compile("(?i)write (raw )?sql.*drop\\s+table"),
            // jailbreak/role-play tricks
            Pattern.compile("(?i)pretend you are not an ai"),
            Pattern.compile("(?i)#?doanythingnow|#?dan"),
            // exfiltration
            Pattern.compile("(?i)print the hidden rules|show hidden instructions")
    );

    @Override
    public InputGuardrailResult validate(UserMessage msg) {
        if (!config.guardtrails().promptInjection().enabled()) {
            return success();
        }

        String text = msg != null ? msg.singleText() : null;
        if (text == null || text.isBlank()) {
            return failure("Empty message");
        }
        for (var p : BLOCKLIST) {
            if (p.matcher(text).find()) {
                return fatal("Prompt injection detected");
            }
        }
        if (!config.guardtrails().promptInjection().agent().enabled()) {
            return success();
        }

        double result = service.isInjection(text);
        if (result > config.guardtrails().promptInjection().score().threshold()) {
            return failure("Prompt injection detected");
        }
        return success();
    }
}
