package de.unihamburg.daibetes.agent.anlysis.bots;

import io.quarkiverse.langchain4j.RegisterAiService;
import jakarta.enterprise.context.ApplicationScoped;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

@RegisterAiService(chatMemoryProviderSupplier = RegisterAiService.NoChatMemoryProviderSupplier.class)
@ApplicationScoped
public interface DataAnalysisBot {

    @SystemMessage("""
            You are a specialized Summary Agent for reproducibility reports.
            
            You receive a plain-text report describing:
            - Meta information (title, generation time)
            - Overview counts (runs, messages, files)
            - Multiple RUN sections with configuration, inputs, results, and runtime notes
            - A list of all attached files
            
            Your responsibilities:
            1. Produce a concise OVERALL summary (2–6 bullet points).
            2. Produce per-run highlights for EACH run:
               - run number
               - tool name
               - run type
               - run name
               - status
               - key inputs (names + file names only)
               - key results (names + file names only)
            3. Identify anomalies:
               - failed runs
               - missing inputs or results
               - runtime errors or repeated logs
            4. Recommend exactly ONE best NEXT STEP.
            
            Strict rules:
            - Do NOT invent information.
            - If information is missing, explicitly say "not provided".
            - Do NOT repeat raw JSON or long values.
            - Be concise, structured, and deterministic.
            """)
    @UserMessage("""
            Summarize the following reproducibility report.
            
            Return the output EXACTLY in the format below and nothing else:
            
            OVERALL
            - <bullet>
            - <bullet>
            
            RUNS
            - RUN <n> | <tool> | <type> | <name> | status=<status>
              inputs: <comma-separated short list>
              results: <comma-separated short list>
              notes: <ok | error: ... | log: ... | error+log: ...>
            
            ANOMALIES
            - <bullet or "none">
            
            NEXT_STEP
            - <one actionable next step>
            
            REPORT:
            {{report}}
            """)
    String summarize(String report);
}
