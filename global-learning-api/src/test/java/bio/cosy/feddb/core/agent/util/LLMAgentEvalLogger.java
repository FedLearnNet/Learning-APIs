package bio.cosy.feddb.core.agent.util;

import bio.cosy.feddb.core.agent.pojo.LLMAgentAnswer;
import bio.cosy.feddb.core.agent.pojo.LLMAgentQuestionResult;
import bio.cosy.feddb.core.agent.pojo.LLMAgentTopic;
import io.quarkus.logging.Log;

import java.nio.file.Path;
import java.util.List;

public final class LLMAgentEvalLogger {

    private LLMAgentEvalLogger() {
    }

    public static void logRunStart(int topicCount,
                                   int totalQuestions,
                                   int cachedCount,
                                   boolean replaceMode) {
        Log.infof(
                "Starting PoSyMed LLM agent evaluation | topics=%d | totalQuestions=%d | cached=%d | replaceMode=%s",
                topicCount,
                totalQuestions,
                cachedCount,
                replaceMode
        );
    }

    public static void logTopicStart(int topicIndex, int totalTopics, LLMAgentTopic topic) {
        int size = topic.getQuestions() != null ? topic.getQuestions().size() : 0;
        Log.infof(
                "---- Topic %d/%d start | topic=%s | questions=%d ----",
                topicIndex,
                totalTopics,
                topic.getTopic(),
                size
        );
    }

    public static void logTopicEnd(String topic,
                                   int executed,
                                   int skipped,
                                   int warnings,
                                   int failed,
                                   List<LLMAgentQuestionResult> topicResults) {
        Log.infof(
                "---- Topic finished | topic=%s | executed=%d | skipped=%d | warnings=%d | failed=%d ----",
                topic,
                executed,
                skipped,
                warnings,
                failed
        );
        logTopicQualitySummary(topic, topicResults);
    }

    public static void logSkip(int globalIndex,
                               int totalQuestions,
                               int topicIndex,
                               int totalTopics,
                               int topicQuestionIndex,
                               int topicQuestionCount,
                               String topic,
                               String question) {
        Log.infof(
                "%s SKIP cached | topic=%s | question=%s",
                prefix(globalIndex, totalQuestions, topicIndex, totalTopics, topicQuestionIndex, topicQuestionCount),
                topic,
                shorten(question, 140)
        );
    }

    public static void logRun(int globalIndex,
                              int totalQuestions,
                              int topicIndex,
                              int totalTopics,
                              int topicQuestionIndex,
                              int topicQuestionCount,
                              String topic,
                              String question) {
        Log.infof(
                "%s RUN | topic=%s | question=%s",
                prefix(globalIndex, totalQuestions, topicIndex, totalTopics, topicQuestionIndex, topicQuestionCount),
                topic,
                shorten(question, 140)
        );
    }

    public static void logQuestionOutcome(LLMAgentQuestionResult result,
                                          long durationMs,
                                          int globalIndex,
                                          int totalQuestions,
                                          int topicIndex,
                                          int totalTopics,
                                          int topicQuestionIndex,
                                          int topicQuestionCount) {
        String prefix = prefix(globalIndex, totalQuestions, topicIndex, totalTopics, topicQuestionIndex, topicQuestionCount);

        if (result == null) {
            Log.warnf("%s FAIL | %d ms | result=null", prefix, durationMs);
            return;
        }

        LLMAgentAnswer answer = result.getAnswer();
        boolean warning = hasWarning(result);
        int judgeScore = result.getJudgeVerdict() != null ? result.getJudgeVerdict().getScore() : -1;
        boolean judgeCorrect = result.getJudgeVerdict() != null && result.getJudgeVerdict().isCorrect();

        String summary = String.format(
                "%s %s | %d ms | depth=%d | done=%s | request=%s | judgeCorrect=%s | judgeScore=%d | question=%s",
                prefix,
                warning ? "WARN" : "SUCCESS",
                durationMs,
                result.getDepth(),
                answer != null && answer.isDone(),
                answer != null && answer.isRequest(),
                judgeCorrect,
                judgeScore,
                shorten(result.getQuestion(), 160)
        );

        if (warning) {
            Log.warn(summary);

            if (answer == null) {
                Log.warnf("%s no answer mapped", prefix);
                return;
            }

            if (answer.getErrorMessage() != null && !answer.getErrorMessage().isBlank()) {
                Log.warnf("%s error=%s", prefix, shorten(answer.getErrorMessage(), 500));
            }

            if (!answer.isDone()) {
                Log.warnf("%s answer not marked done | status=%s", prefix, shorten(answer.getStatusMessage(), 300));
            }

            if (answer.getStatusMessage() != null && !answer.getStatusMessage().isBlank()) {
                Log.warnf("%s status=%s", prefix, shorten(answer.getStatusMessage(), 500));
            }

            if (result.getJudgeVerdict() != null && !result.getJudgeVerdict().isCorrect()) {
                Log.warnf(
                        "%s judge explanation=%s",
                        prefix,
                        shorten(result.getJudgeVerdict().getExplanation(), 500)
                );
            }

            if (answer.getMessage() == null || answer.getMessage().isBlank()) {
                Log.warnf("%s final message is empty", prefix);
            }

            if (answer.getHumanInTheLoop() != null && !answer.getHumanInTheLoop().isEmpty()) {
                Log.warnf("%s HITL entries present=%d", prefix, answer.getHumanInTheLoop().size());
            }

            if (answer.getTools() != null && !answer.getTools().isEmpty()) {
                Log.warnf("%s tools observed=%s", prefix, answer.getTools().stream()
                        .map(t -> t != null ? t.getName() : null)
                        .toList());
            }
        } else {
            Log.info(summary);
        }
    }

    public static void logRunEnd(int totalQuestions,
                                 int executed,
                                 int skipped,
                                 int warnings,
                                 int failed,
                                 Path resultPath) {
        Log.infof(
                "Evaluation finished | totalQuestions=%d | executed=%d | skipped=%d | warnings=%d | failed=%d | resultsPath=%s",
                totalQuestions,
                executed,
                skipped,
                warnings,
                failed,
                resultPath
        );
    }

    public static boolean hasWarning(LLMAgentQuestionResult result) {
        if (result == null) {
            return true;
        }

        if (result.getAnswer() == null) {
            return true;
        }

        if (result.getAnswer().getErrorMessage() != null
                && !result.getAnswer().getErrorMessage().isBlank()) {
            return true;
        }

        if (!result.getAnswer().isDone()) {
            return true;
        }

        if (result.getAnswer().getMessage() == null
                || result.getAnswer().getMessage().isBlank()) {
            return true;
        }

        return result.getJudgeVerdict() == null || !result.getJudgeVerdict().isCorrect();
    }

    public static void logTopicQualitySummary(String topic, List<LLMAgentQuestionResult> topicResults) {
        long total = topicResults != null ? topicResults.size() : 0;
        long withAnswer = topicResults == null ? 0 : topicResults.stream()
                .filter(r -> r.getAnswer() != null)
                .count();

        long done = topicResults == null ? 0 : topicResults.stream()
                .filter(r -> r.getAnswer() != null && r.getAnswer().isDone())
                .count();

        long judgeCorrect = topicResults == null ? 0 : topicResults.stream()
                .filter(r -> r.getJudgeVerdict() != null && r.getJudgeVerdict().isCorrect())
                .count();

        long withErrors = topicResults == null ? 0 : topicResults.stream()
                .filter(r -> r.getAnswer() != null
                        && r.getAnswer().getErrorMessage() != null
                        && !r.getAnswer().getErrorMessage().isBlank())
                .count();

        double avgScore = topicResults == null ? 0 : topicResults.stream()
                .filter(r -> r.getJudgeVerdict() != null)
                .mapToInt(r -> r.getJudgeVerdict().getScore())
                .average()
                .orElse(0);

        Log.infof(
                "Topic quality summary | topic=%s | total=%d | withAnswer=%d | done=%d | withErrors=%d | judgeCorrect=%d | avgJudgeScore=%.2f",
                topic,
                total,
                withAnswer,
                done,
                withErrors,
                judgeCorrect,
                avgScore
        );
    }

    private static String prefix(int globalIndex,
                                 int totalQuestions,
                                 int topicIndex,
                                 int totalTopics,
                                 int topicQuestionIndex,
                                 int topicQuestionCount) {
        return String.format(
                "[%d/%d] [Topic %d/%d | %d/%d]",
                globalIndex,
                totalQuestions,
                topicIndex,
                totalTopics,
                topicQuestionIndex,
                topicQuestionCount
        );
    }

    private static String shorten(String text, int maxLen) {
        if (text == null) {
            return null;
        }
        if (text.length() <= maxLen) {
            return text;
        }
        return text.substring(0, Math.max(0, maxLen - 3)) + "...";
    }
}
