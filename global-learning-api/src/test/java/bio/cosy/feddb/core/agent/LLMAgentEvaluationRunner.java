package bio.cosy.feddb.core.agent;

import bio.cosy.feddb.core.agent.judge.LLMAgentJudgeService;
import bio.cosy.feddb.core.agent.pojo.LLMAgentAnswer;
import bio.cosy.feddb.core.agent.pojo.LLMAgentQuestion;
import bio.cosy.feddb.core.agent.pojo.LLMAgentQuestionResult;
import bio.cosy.feddb.core.agent.util.TestFileUploader;
import bio.cosy.feddb.core.api.model.workflow.chat.ModelWorkflowChatMessageDTO;
import de.unihamburg.daibetes.agent.anlysis.ModelWorkflowAgent;
import de.unihamburg.daibetes.api.analysis.DataAnalysisBO;
import de.unihamburg.daibetes.api.analysis.chat.DataAnalysisChatMessageBO;
import de.unihamburg.daibetes.api.analysis.chat.hitl.HumanInTheLoopRequestBO;
import de.unihamburg.daibetes.api.analysis.chat.hitl.HumanInTheLoopRequestDTO;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class LLMAgentEvaluationRunner {
    private static final int MAX_HITL_ROUNDS = 3;

    @Inject
    ModelWorkflowAgent modelWorkflowAgent;

    @Inject
    LLMAgentJudgeService judgeService;

    @Inject
    DataAnalysisBO dataAnalysisBO;

    @Inject
    HumanInTheLoopRequestBO humanInTheLoopRequestBO;

    @Inject
    DataAnalysisChatMessageBO dataAnalysisChatMessageBO;

    @Inject
    TestFileUploader testFileUploader;

    public LLMAgentQuestionResult evaluateSingleQuestion(String topic,
                                                         LLMAgentQuestion question,
                                                         int depth) {
        try {
            String keycloakId = syntheticUserId();
            Long workflowId = createEvaluationWorkflow(keycloakId);

            testFileUploader.uploadRequiredFiles(workflowId, question.getRequiredFiles(), keycloakId);
            LLMAgentQuestionResult result = new LLMAgentQuestionResult();
            copyQuestionIntoResult(question, result);
            result.setTopic(topic);
            result.setDepth(depth);

            ModelWorkflowChatMessageDTO run = runAgent(workflowId, keycloakId, question.getQuestion());
            if (run == null) {
                Log.errorf("Agent did not return any answer for question: %s", question.getQuestion());
                return result;
            }
            LLMAgentAnswer latestAnswer = new LLMAgentAnswer(run);
            result.setAnswer(latestAnswer);

            for (int round = 0; round < MAX_HITL_ROUNDS && latestAnswer.isWaitingForHitl(); round++) {
                String hitlReply = round == 0
                        ? question.getHitlAnswer()
                        : "Please continue with your best grounded recommendation based on the available context.";

                ModelWorkflowChatMessageDTO hitlRun = runAgent(workflowId, keycloakId, hitlReply);
                latestAnswer = new LLMAgentAnswer(hitlRun);
                result.setAnswer(latestAnswer);
            }

            result.setJudgeVerdict(judgeService.judgeAnswer(
                    question.getQuestion(),
                    question.getExpectedAnswer(),
                    latestAnswer.getMessage()
            ));

            return result;
        } catch (Exception e) {
            LLMAgentQuestionResult failed = new LLMAgentQuestionResult();
            copyQuestionIntoResult(question, failed);
            failed.setTopic(topic);
            failed.setDepth(depth);

            LLMAgentAnswer answer = new LLMAgentAnswer();
            answer.setErrorMessage(e.getMessage());
            answer.setDone(false);
            failed.setAnswer(answer);

            failed.setJudgeVerdict(judgeService.judgeAnswer(
                    question.getQuestion(),
                    question.getExpectedAnswer(),
                    null
            ));

            Log.error("Evaluation failed for question: " + question.getQuestion(), e);
            return failed;
        }
    }

    private ModelWorkflowChatMessageDTO runAgent(Long workflowId, String keycloakId, String userRequest) {
        Optional<HumanInTheLoopRequestDTO> hitl = humanInTheLoopRequestBO.findPendingByDataAnalysisTransactional(workflowId, keycloakId);

        ModelWorkflowChatMessageDTO created;
        if (hitl.isPresent()) {
            Log.debugf("There is a pending HITL request for workflow %s, using existing message %s", workflowId, hitl.get().getMessageId());
            created = dataAnalysisChatMessageBO.getByIdTransactional(hitl.get().getMessageId());
        } else {
            created = dataAnalysisChatMessageBO.create("", workflowId, false);
        }

        return modelWorkflowAgent.answer(workflowId, created, userRequest, keycloakId)
                .collect()
                .last()
                .await()
                .indefinitely();
    }

    private void copyQuestionIntoResult(LLMAgentQuestion source, LLMAgentQuestionResult target) {
        target.setQuestion(source.getQuestion());
        target.setExpectedAnswer(source.getExpectedAnswer());
        target.setHitlAnswer(source.getHitlAnswer());
        target.setNeededTools(
                source.getNeededTools() == null
                        ? new ArrayList<>()
                        : new ArrayList<>(source.getNeededTools())
        );
        target.setRequiredFiles(
                source.getRequiredFiles() == null
                        ? new ArrayList<>()
                        : new ArrayList<>(source.getRequiredFiles())
        );
    }

    @Transactional
    public Long createEvaluationWorkflow(String keycloakId) {
        return dataAnalysisBO.createWorkflow(keycloakId, "Evaluation Workflow - " + UUID.randomUUID()).getId();
    }

    private static String syntheticUserId() {
        return "test";
    }
}
