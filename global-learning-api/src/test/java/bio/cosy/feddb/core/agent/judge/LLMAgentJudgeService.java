package bio.cosy.feddb.core.agent.judge;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class LLMAgentJudgeService {

    @Inject
    LLMAgentAnswerJudge judge;

    public LLMJudgeVerdictDTO judgeAnswer(String question, String expectedAnswer, String actualAnswer) {
        if (actualAnswer == null || actualAnswer.isBlank()) {
            return new LLMJudgeVerdictDTO("No answer returned.");
        }

        try {
            return judge.judge(question, expectedAnswer, actualAnswer);
        } catch (Exception e) {
            Log.error("LLM judge failed", e);
            return new LLMJudgeVerdictDTO("Judge failed: " + e.getMessage());
        }
    }
}
