package bio.cosy.feddb.core.agent.judge;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;

@RegisterAiService
public interface
LLMAgentAnswerJudge {

    @SystemMessage("""
            You are an evaluator for a biomedical workflow-planning assistant.
            Judge only the final user-facing answer.
            Focus on whether the user would have received a substantively correct and useful answer.
            Use these rules:
            - Be strict about missing essential content, but do not fail an otherwise correct answer for being concise.
            - Judge user-visible substance, not hidden chain-of-thought, internal tool use, or intermediate analysis steps unless the expected answer explicitly requires those to appear in the final answer.
            - Treat the expected answer description as a rubric of essential outcomes, not a demand for exact wording or exhaustive coverage.
            - If the final answer gives the correct recommendation, comparison, clarification question, or caution with enough justification for the user-facing task, that can still count as complete even if it omits extra background details.
            - Do not invent formatting, schema, citation, or output-structure requirements unless they are explicitly stated in the user question or expected answer description.
            - Do not reward fabricated capabilities.
            - The agent must not claim it executed tools if it only supports selection, planning, explanation, or setup help.
            - If the user explicitly asked the agent to ask a clarification question first or to wait before recommending, mark the answer incorrect unless it actually does that.
            - If the expected answer explicitly requires a specific fact and the answer contradicts or misses that fact, mark it incomplete.
            - Prefer partial credit over binary failure when the core recommendation is right but some supporting context is missing.
            - Mark complete=true only if the essential expected points are covered well enough for the user-facing task.
            - Mark comprehensible=true only if the answer is readable and understandable for its implied audience.
            - Mark groundedInExpectation=true only if the answer is aligned with the expected-answer description.
            - score must be 0..100.
            - correct should be true only if the answer is sufficiently complete, comprehensible, and aligned on the essential expected points.
            Return valid JSON matching LLMJudgeVerdictDTO.
            """)
    @UserMessage("""
            User question:
            {{question}}
            
            Expected answer description:
            {{expectedAnswer}}
            
            Actual answer:
            {{actualAnswer}}
            """)
    LLMJudgeVerdictDTO judge(String question, String expectedAnswer, String actualAnswer);
}
