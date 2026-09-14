package de.unihamburg.daibetes.agent.anlysis;

import bio.cosy.feddb.core.api.model.chat.ToolDTO;
import bio.cosy.feddb.core.api.model.workflow.chat.ModelWorkflowChatMessageDTO;
import bio.cosy.feddb.core.api.model.workflow.chat.PlanStep;
import bio.cosy.feddb.core.api.model.workflow.chat.ReasoningDTO;
import bio.cosy.feddb.core.api.store.StoreDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.unihamburg.daibetes.agent.anlysis.bots.*;
import de.unihamburg.daibetes.agent.anlysis.tools.*;
import de.unihamburg.daibetes.api.analysis.DataAnalysisBO;
import de.unihamburg.daibetes.api.analysis.chat.hitl.HumanInTheLoopRequestBO;
import de.unihamburg.daibetes.api.analysis.chat.hitl.HumanInTheLoopRequestDTO;
import de.unihamburg.daibetes.services.research.ResearchService;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import io.quarkus.logging.Log;
import io.smallrye.mutiny.subscription.MultiEmitter;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class PlanningAgent {
    private static final int MAX_STEPS = 6;

    @Inject
    DecisionPlannerBot planner;

    @Inject
    ChatMemoryStore chatMemoryStore;

    @Inject
    ModelWorkflowFilesTools filesTools;

    @Inject
    FinalizeBot finalizeBot;

    @Inject
    DataAnalysisTools dataAnalysisTools;

    @Inject
    DataAnalysisBot dataAnalysisBot;

    @Inject
    ResearchBot researchBot;


    @Inject
    ResultAnalyzerTools resultAnalyzerTools;

    @Inject
    PlanNextStepBot planNextStepBot;

    @Inject
    PlanNextStepTools planNextStepTools;

    @Inject
    StoreBot storeBot;

    @Inject
    DataAnalysisBO dataAnalysisBO;

    @Inject
    HumanInTheLoopRequestBO humanInTheLoopRequestBO;

    private final ObjectMapper om = new ObjectMapper();

    public AgentResult planAnswer(Long workflowId,
                                  String keycloakId,
                                  String userGoal,
                                  String context,
                                  ModelWorkflowChatMessageDTO current,
                                  Optional<HumanInTheLoopRequestDTO> hitl,
                                  MultiEmitter<? super ModelWorkflowChatMessageDTO> em) {
        PlanState state = null;
        if (hitl.isPresent()) {
            state = hitl.get().getState();
            state.addHumanInTheLoop(hitl.get().getRequest(), hitl.get().getAnswer());
        } else {
            state = new PlanState();
            state.setUserGoal(userGoal);
            state.setWorkflowId(workflowId);
            state.setPreviousContext(context);
        }
        List<PlanStep> history = new ArrayList<>();
        resetMemory(workflowId, state, 0);
        for (int step = 1; step <= MAX_STEPS; step++) {
            int remainingSteps = MAX_STEPS - step + 1;
            PlanStep decision = planner.decide(state, history, remainingSteps);
            Log.infof("Planning step %d decision: %s", step, safeToString(decision));
            history.add(decision);
            em.emit(ModelWorkflowChatMessageDTO.snapshot(current, c -> c.addReasoning(new ReasoningDTO(decision))));
            Log.debugf("Planning step %d: %s", step, safeToString(decision));

            String currentSubQuestion = decision.getSubTaskQuestion();
            Log.debugf("Planning step %d: %s", step, safeToString(decision));

            if (decision.getAction() == null) break;

            switch (decision.getAction()) {
                case SUMMARIZE_PREVIOUS_WORK -> {
                    summarizeWorkflow(state, workflowId, keycloakId, current, em);
                }
                case PLAN_NEXT_STEP -> {
                    summarizeWorkflow(state, workflowId, keycloakId, current, em);
                    PlanNextStepDecision nextStepDecision = planNextStepBot.nextTool(workflowId.toString(), state, state.getTools(), currentSubQuestion);
                    if (nextStepDecision.getStoreId() != null) {
                        StoreDTO store = planNextStepTools.getStoreById(nextStepDecision.getStoreId(), nextStepDecision.getKind(), workflowId.toString());
                        state.setNextStoreItem(store);
                    }
                    state.setNextStepDecision(nextStepDecision);
                    if (nextStepDecision.getFollowUpQuestion() != null) {
                        humanInTheLoopRequestBO.createTransactional(nextStepDecision.getFollowUpQuestion(), current, state);
                        em.emit(current);
                        return AgentResult.humanInTheLoopNeeded();
                    }
                }
                case ANALYZE_DATA -> {
                    try {
                        Long fileId = extractIdFromSubTaskQuestion(currentSubQuestion);
                        ToolDTO toolDTO = new ToolDTO("ANALYZE_DATA");
                        toolDTO.setInput("File: " + fileId);
                        em.emit(ModelWorkflowChatMessageDTO.snapshot(current, c -> c.addTool(toolDTO)));
                        String subQuestion = "We need to analyze the file because of: "
                                + decision.getReason()
                                + "\n\nPlease analyze the file and provide insights that can help to achieve the user's goal: "
                                + userGoal;
                        String analysisResult = resultAnalyzerTools.analyzeFileById(workflowId, fileId, keycloakId, subQuestion);
                        state.addFileAnalyzeResult(fileId, analysisResult);
                        toolDTO.addContent(analysisResult);
                        toolDTO.setStop();
                        em.emit(ModelWorkflowChatMessageDTO.snapshot(current, c -> c.updateTool(toolDTO)));

                    } catch (Exception e) {
                        state.setFeedback(e.getMessage());
                    }
                }
                case RESEARCH_PAPERS -> {
                    state.setPapers(researchBot.answer(workflowId.toString(), currentSubQuestion, context));
                }
                case EXTERNAL_AGENT -> {
                    //FOR FUTURE USE, NOT IMPLEMENTED YET, NOT PLANNED SOON
                }
                case HUMAN_IN_THE_LOOP -> {
                    humanInTheLoopRequestBO.createTransactional(currentSubQuestion, current, state);
                    em.emit(current);
                    return AgentResult.humanInTheLoopNeeded();
                }
                case FETCH_TOOLS -> {
                    state.addTool(storeBot.answer(currentSubQuestion));
                }
                case FETCH_DATA -> {
                    if (workflowId == null) {
                        state.setDataProfiles(filesTools.listFiles(keycloakId));
                    } else {
                        state.setDataProfiles(filesTools.listFiles(workflowId, keycloakId));
                    }
                    summarizeWorkflow(state, workflowId, keycloakId, current, em);
                }
                case FINALIZE -> {
                    String result = finalizeBot.answer(workflowId.toString(), userGoal, state)
                            .onItem().invoke(chunk -> em.emit(ModelWorkflowChatMessageDTO.snapshot(current, c -> c.addChunk(chunk))))
                            .onFailure().invoke(t -> em.emit(ModelWorkflowChatMessageDTO.snapshot(current, c -> c.setErrorMessage(t.getMessage()))))
                            .onCompletion().invoke(() -> em.emit(ModelWorkflowChatMessageDTO.snapshot(current, c -> c.addChunk(""))))
                            .collect()
                            .asList()
                            .map(list -> String.join("", list))
                            .await()
                            .indefinitely();
                    if (state.getNextStepDecision() != null) {
                        return AgentResult.fromMessageAndNextStep(result, state.getNextStepDecision());
                    }
                    return AgentResult.fromMessage(result);
                }
            }
        }
        return AgentResult.fromMessage("Could not complete planning. Try asking: \"recommend a model for <task>\" or provide a workflow id for data-fit analysis.");
    }

    public void summarizeWorkflow(PlanState state, Long workflowId, String keycloakId, ModelWorkflowChatMessageDTO current, MultiEmitter<? super ModelWorkflowChatMessageDTO> em) {
        if (StringUtils.isEmpty(state.getDataAnalysisSummary())) {
            ToolDTO toolCall = new ToolDTO("Summarize Data Analysis Report");
            String summary = null;
            Optional<String> existingSummary = dataAnalysisBO.getLLSummaryOptionalTransactional(workflowId, keycloakId);
            if (existingSummary.isPresent()) {
                summary = existingSummary.get();
            }
            em.emit(ModelWorkflowChatMessageDTO.snapshot(current, c -> c.addTool(toolCall)));
            if (existingSummary.isPresent()) {
                toolCall.setInput("Use existing summary.");
            } else if (StringUtils.isEmpty(state.getDataAnalysisReport())) {
                String report = dataAnalysisTools.getAnalysisReportTransactional(workflowId, keycloakId);
                toolCall.setInput(report);
                em.emit(ModelWorkflowChatMessageDTO.snapshot(current, c -> c.updateTool(toolCall)));
                state.setDataAnalysisReport(report);
            }
            if (existingSummary.isEmpty()) {
                summary = dataAnalysisBot.summarize(state.getDataAnalysisReport());
                dataAnalysisBO.setLLMSummaryTransactional(workflowId, summary, keycloakId);
            }
            toolCall.addContent(summary);
            toolCall.setStop();
            em.emit(ModelWorkflowChatMessageDTO.snapshot(current, c -> c.updateTool(toolCall)));
            state.setDataAnalysisSummary(summary);
        }
    }

    private void resetMemory(Long workflowId, PlanState state, int step) {
        Log.infof("Clean memory for step %d", step);
        chatMemoryStore.deleteMessages(workflowId);
        if (StringUtils.isNotEmpty(state.getPreviousContext())) {
            Log.infof("Resetting memory for %s", state.getPreviousContext());
            UserMessage context = new UserMessage(state.getPreviousContext());
            ArrayList<ChatMessage> messages = new ArrayList<>();
            messages.add(context);
            chatMemoryStore.updateMessages(workflowId, messages);
        }
    }

    private String safeToString(PlanStep d) {
        try {
            return om.writeValueAsString(d);
        } catch (Exception e) {
            return String.valueOf(d);
        }
    }

    private Long extractIdFromSubTaskQuestion(String input) {
        if (StringUtils.isBlank(input)) {
            throw new IllegalArgumentException("SubTaskQuestion is empty");
        }

        try {
            return Long.parseLong(input.trim());
        } catch (NumberFormatException ignored) {
            // fall through
        }

        var matcher = java.util.regex.Pattern.compile("(\\d+)").matcher(input);
        if (matcher.find()) {
            return Long.parseLong(matcher.group(1));
        }

        throw new IllegalArgumentException("No numeric ID found in SubTaskQuestion: " + input);
    }
}
