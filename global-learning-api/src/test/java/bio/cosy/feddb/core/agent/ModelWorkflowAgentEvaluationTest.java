package bio.cosy.feddb.core.agent;

import bio.cosy.feddb.core.agent.pojo.LLMAgentEvalProgressDTO;
import bio.cosy.feddb.core.agent.pojo.LLMAgentQuestion;
import bio.cosy.feddb.core.agent.pojo.LLMAgentQuestionResult;
import bio.cosy.feddb.core.agent.pojo.LLMAgentTopic;
import bio.cosy.feddb.core.agent.util.JsonFileCache;
import bio.cosy.feddb.core.agent.util.JsonLoader;
import bio.cosy.feddb.core.agent.util.LLMAgentEvalLogger;
import bio.cosy.feddb.core.agent.util.LLMAgentQuestionResultUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import io.quarkus.logging.Log;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.ConfigProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@QuarkusTest
public class ModelWorkflowAgentEvaluationTest {

    private static final boolean REPLACE_MODE = false;
    private static final boolean FILTER_ERRORS = true;
    private static final boolean FILTER_ERRORS_SAVING = true;

    private static final String TESTSET_PATH = "eval/agent/agent_evaluation_testset.json";

    private static final String MODEL_NAME = ConfigProvider.getConfig()
            .getValue("quarkus.langchain4j.openai.chat-model.model-name", String.class);

    private static List<LLMAgentQuestionResult> cachedResults = new ArrayList<>();

    @Inject
    LLMAgentEvaluationRunner evaluationRunner;

    @BeforeAll
    static void setup() {
        if (REPLACE_MODE) {
            Log.warn("REPLACE MODE is ON - existing eval results will be overwritten.");
            cachedResults = new ArrayList<>();
            return;
        }

        cachedResults = JsonFileCache.loadList(
                getResultPath(),
                new TypeReference<List<LLMAgentQuestionResult>>() {
                }
        );

        if (FILTER_ERRORS) {
            cachedResults = LLMAgentQuestionResultUtils.getFilteredResults(cachedResults);
        }
        if (FILTER_ERRORS_SAVING) {
            JsonFileCache.write(getResultPath(), cachedResults);
        }

        Log.infof("Loaded %d cached eval results from %s", cachedResults.size(), getResultPath());
    }

    @Test
    @Order(1)
    void evaluateModelWorkflowAgent() {
        List<LLMAgentTopic> topics = JsonLoader.loadJson(
                TESTSET_PATH,
                new TypeReference<List<LLMAgentTopic>>() {
                }
        );

        syncCachedQuestionMetadata(topics);

        int totalQuestions = countAllQuestions(topics);
        int alreadyCached = countCachedQuestions(topics);

        LLMAgentEvalProgressDTO progress = new LLMAgentEvalProgressDTO();
        LLMAgentEvalLogger.logRunStart(topics.size(), totalQuestions, alreadyCached, REPLACE_MODE);

        for (int topicIndex = 0; topicIndex < topics.size(); topicIndex++) {
            LLMAgentTopic topic = topics.get(topicIndex);
            List<LLMAgentQuestion> topicQuestions = Optional.ofNullable(topic.getQuestions()).orElse(List.of());
            List<LLMAgentQuestionResult> topicResults = new ArrayList<>();

            LLMAgentEvalLogger.logTopicStart(topicIndex + 1, topics.size(), topic);

            int topicExecuted = 0;
            int topicSkipped = 0;
            int topicWarnings = 0;
            int topicFailed = 0;

            for (int questionIndex = 0; questionIndex < topicQuestions.size(); questionIndex++) {
                LLMAgentQuestion question = topicQuestions.get(questionIndex);
                int currentGlobal = progress.getGlobalProcessed() + 1;

                if (!REPLACE_MODE && alreadyDone(topic.getTopic(), question.getQuestion(), questionIndex)) {
                    topicSkipped++;
                    progress.increaseSkipped();
                    progress.increaseProcessed();

                    LLMAgentEvalLogger.logSkip(
                            currentGlobal,
                            totalQuestions,
                            topicIndex + 1,
                            topics.size(),
                            questionIndex + 1,
                            topicQuestions.size(),
                            topic.getTopic(),
                            question.getQuestion()
                    );
                    continue;
                }

                LLMAgentEvalLogger.logRun(
                        currentGlobal,
                        totalQuestions,
                        topicIndex + 1,
                        topics.size(),
                        questionIndex + 1,
                        topicQuestions.size(),
                        topic.getTopic(),
                        question.getQuestion()
                );

                long start = System.currentTimeMillis();
                LLMAgentQuestionResult result = evaluationRunner.evaluateSingleQuestion(topic.getTopic(), question, questionIndex);
                long durationMs = System.currentTimeMillis() - start;

                cachedResults.add(result);
                topicResults.add(result);

                if (LLMAgentEvalLogger.hasWarning(result)) {
                    topicWarnings++;
                    progress.increaseWarnings();
                }
                if (result.getAnswer() == null || result.getAnswer().getErrorMessage() != null) {
                    topicFailed++;
                    progress.increaseFailed();
                }

                JsonFileCache.write(getResultPath(), cachedResults);

                topicExecuted++;
                progress.increaseExecuted();
                progress.increaseProcessed();

                LLMAgentEvalLogger.logQuestionOutcome(
                        result,
                        durationMs,
                        currentGlobal,
                        totalQuestions,
                        topicIndex + 1,
                        topics.size(),
                        questionIndex + 1,
                        topicQuestions.size()
                );
            }

            LLMAgentEvalLogger.logTopicEnd(
                    topic.getTopic(),
                    topicExecuted,
                    topicSkipped,
                    topicWarnings,
                    topicFailed,
                    topicResults
            );
        }

        LLMAgentEvalLogger.logRunEnd(
                totalQuestions,
                progress.getGlobalExecuted(),
                progress.getGlobalSkipped(),
                progress.getGlobalWarnings(),
                progress.getGlobalFailed(),
                getResultPath()
        );
    }


    private int countAllQuestions(List<LLMAgentTopic> topics) {
        int count = 0;
        for (LLMAgentTopic topic : topics) {
            count += Optional.ofNullable(topic.getQuestions()).orElse(List.of()).size();
        }
        return count;
    }

    private int countCachedQuestions(List<LLMAgentTopic> topics) {
        int count = 0;
        for (LLMAgentTopic topic : topics) {
            List<LLMAgentQuestion> topicQuestions = Optional.ofNullable(topic.getQuestions()).orElse(List.of());
            for (int questionIndex = 0; questionIndex < topicQuestions.size(); questionIndex++) {
                if (findCachedResult(topic.getTopic(), questionIndex).isPresent()) {
                    count++;
                }
            }
        }
        return count;
    }

    private boolean alreadyDone(String topic, String question, int depth) {
        return findCachedResult(topic, depth)
                .filter(item -> Objects.equals(question, item.getQuestion()))
                .isPresent();
    }

    private Optional<LLMAgentQuestionResult> findCachedResult(String topic, int depth) {
        return JsonFileCache.findFirst(
                cachedResults,
                item -> topic.equals(item.getTopic())
                        && depth == item.getDepth()
        );
    }

    private void syncCachedQuestionMetadata(List<LLMAgentTopic> topics) {
        boolean changed = false;

        for (int topicIndex = 0; topicIndex < topics.size(); topicIndex++) {
            LLMAgentTopic topic = topics.get(topicIndex);
            List<LLMAgentQuestion> topicQuestions = Optional.ofNullable(topic.getQuestions()).orElse(List.of());

            for (int questionIndex = 0; questionIndex < topicQuestions.size(); questionIndex++) {
                LLMAgentQuestion question = topicQuestions.get(questionIndex);
                Optional<LLMAgentQuestionResult> cachedResultOptional = findCachedResult(topic.getTopic(), questionIndex);

                if (cachedResultOptional.isEmpty()) {
                    continue;
                }

                LLMAgentQuestionResult cachedResult = cachedResultOptional.get();
                boolean metadataChanged = false;

                if (!Objects.equals(cachedResult.getQuestion(), question.getQuestion())) {
                    metadataChanged = true;
                }
                if (!Objects.equals(cachedResult.getNeededTools(), question.getNeededTools())) {
                    metadataChanged = true;
                }
                if (!Objects.equals(cachedResult.getExpectedAnswer(), question.getExpectedAnswer())) {
                    metadataChanged = true;
                }
                if (!Objects.equals(cachedResult.getHitlAnswer(), question.getHitlAnswer())) {
                    metadataChanged = true;
                }
                if (!Objects.equals(cachedResult.getRequiredFiles(), question.getRequiredFiles())) {
                    metadataChanged = true;
                }

                if (metadataChanged) {
                    cachedResults.remove(cachedResult);
                    changed = true;
                    Log.infof("Invalidated cached eval result for topic '%s' depth %d because the testset metadata changed.",
                            topic.getTopic(),
                            questionIndex);
                }
            }
        }

        if (changed) {
            JsonFileCache.write(getResultPath(), cachedResults);
            Log.infof("Synchronized cached eval metadata with current testset for %s", getResultPath());
        }
    }


    static Path getResultPath() {
        return Paths.get("results", "eval", MODEL_NAME.replace(":latest", ""), "llm-agent.json");
    }
}
