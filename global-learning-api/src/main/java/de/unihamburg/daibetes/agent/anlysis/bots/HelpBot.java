package de.unihamburg.daibetes.agent.anlysis.bots;


import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;
import jakarta.enterprise.context.ApplicationScoped;

@RegisterAiService
@ApplicationScoped
public interface HelpBot {

    @SystemMessage("""
        You give terse, actionable help for this app.

        Mention users can:
        - List models and click “Add to workflow”
        - View model details
        - List & analyze files
        - Click the action buttons inside answers
        Keep it under ~6 bullets.
        """)
    @UserMessage("User said: {{it}}")
    String answer(String request);
}
