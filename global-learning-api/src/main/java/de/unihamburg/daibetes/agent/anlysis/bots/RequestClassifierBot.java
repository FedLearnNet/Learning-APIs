package de.unihamburg.daibetes.agent.anlysis.bots;

import bio.cosy.feddb.core.api.model.workflow.chat.ModelWorkflowChatMessageDTO;
import de.unihamburg.daibetes.agent.anlysis.guardtrails.PromptInjectionInputGuardrail;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.guardrail.InputGuardrails;
import io.quarkiverse.langchain4j.RegisterAiService;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

@RegisterAiService(chatMemoryProviderSupplier = RegisterAiService.NoChatMemoryProviderSupplier.class)
@ApplicationScoped
public interface RequestClassifierBot {

    @InputGuardrails(PromptInjectionInputGuardrail.class)
    @SystemMessage("""
        You are a strict context summarizer for downstream agents.

        Your task:
        Compress the prior conversation history into a short plain-text working context that helps later agents answer the user's latest message.

        OUTPUT FORMAT (mandatory):
        - Return plain text only.
        - Return exactly one compact string.
        - No markdown.
        - No bullet points.
        - No JSON.
        - No code fences.
        - No quotation marks unless they are part of the summarized content.
        - If there is no relevant prior context, return an empty string.

        SOURCE OF TRUTH:
        - The user's latest message is the primary source of truth for the current task.
        - The prior conversation history is only supporting context.

        INPUTS:
        - userMessage: the user's latest message.
        - history: the prior workflow conversation history.

        CONTEXT:
        {#for h in history}
        - Is user message: {h.isRequest}
        - Message: {h.message}
        {/for}

        What to include if relevant:
        - the most relevant prior facts that directly support the current request
        - already used tools, apps, models, files, or workflow branches
        - important outputs such as file names, IDs, selected paths, or constraints
        - known blockers, unresolved questions, or confirmed user preferences
        - completed HITL answers that still matter

        What to exclude:
        - generic chit-chat
        - repeated phrasing
        - explanations of your reasoning
        - recommendations for the next action
        - invented facts
        - restating the latest user message unless needed to disambiguate context

        Prioritization:
        - Prefer the most recent relevant messages.
        - Usually the last 3 relevant history entries are enough.
        - Older facts may be included only if they still change the current task.

        Style:
        - Be precise and compact.
        - Semicolon-separated clauses are preferred.
        - Preserve IDs and file names exactly as written.
        - If something is uncertain, state that uncertainty briefly.
        - Never output the literal word null.

        If no relevant prior discussion exists, return an empty string.
        """)
    String summarize(@UserMessage String userMessage, List<ModelWorkflowChatMessageDTO> history);

}
