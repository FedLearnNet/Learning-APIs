package bio.cosy.feddb.core.agent.util;

import bio.cosy.feddb.core.agent.pojo.LLMAgentQuestionResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class LLMAgentQuestionResultUtils {
    public static List<LLMAgentQuestionResult> getFilteredResults(List<LLMAgentQuestionResult> results) {
        if (results == null) {
            return new ArrayList<>();
        }

        return results.stream()
                .filter(Objects::nonNull)
                .filter(LLMAgentQuestionResultUtils::isValidResult)
                .collect(Collectors.toList());
    }

    public static boolean isValidResult(LLMAgentQuestionResult result) {
        if (result == null) {
            return false;
        }

        if (result.getJudgeVerdict() == null || !result.getJudgeVerdict().isCorrect()) {
            return false;
        }

        if (result.getAnswer() == null || !result.getAnswer().isDone()) {
            return false;
        }

        return true;
    }
}
