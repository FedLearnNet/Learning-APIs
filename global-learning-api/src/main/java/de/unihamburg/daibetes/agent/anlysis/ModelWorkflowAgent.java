package de.unihamburg.daibetes.agent.anlysis;

import bio.cosy.feddb.core.api.model.chat.ToolDTO;
import bio.cosy.feddb.core.api.model.workflow.chat.ModelWorkflowChatMessageDTO;
import de.unihamburg.daibetes.agent.anlysis.bots.RequestClassifierBot;
import de.unihamburg.daibetes.api.analysis.chat.DataAnalysisChatMessageBO;
import de.unihamburg.daibetes.api.analysis.chat.hitl.HumanInTheLoopRequestBO;
import de.unihamburg.daibetes.api.analysis.chat.hitl.HumanInTheLoopRequestDTO;
import io.quarkus.logging.Log;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.subscription.MultiEmitter;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class ModelWorkflowAgent {
    public static final String HITL_STATUS_TEXT = "Waiting for your answer";

    @Inject
    PlanningAgent planningAgent;

    @Inject
    HumanInTheLoopRequestBO humanInTheLoopRequestBO;

    @Inject
    DataAnalysisChatMessageBO dataAnalysisChatMessageBO;

    @Inject
    RequestClassifierBot requestClassifierBot;

    @ActivateRequestContext
    public Multi<ModelWorkflowChatMessageDTO> answer(Long workflowId, ModelWorkflowChatMessageDTO content, String request, String keycloakId) {
        return Multi.createFrom().emitter(em -> {
            try {
                Optional<HumanInTheLoopRequestDTO> hitl = humanInTheLoopRequestBO.findPendingByDataAnalysisTransactional(workflowId, keycloakId);
                String context = "";
                if (hitl.isPresent()) {
                    humanInTheLoopRequestBO.setAnsweredTransactional(hitl.get(), request);
                } else {
                    context = getContext(keycloakId, workflowId, content, request, em);
                }
                em.emit(ModelWorkflowChatMessageDTO.snapshot(content, c -> c.setStatusMessage("Starting to process your request...")));
                AgentResult result = planningAgent.planAnswer(workflowId, keycloakId, request, context, content, hitl, em);
                em.emit(ModelWorkflowChatMessageDTO.snapshot(content, c -> {
                    if (!result.isNeedsHumanInTheLoopDTO()) {
                        c.setMessage(result.getMessageMarkdown());
                        c.setAction(result.getUiAction());
                        c.setDone(true);
                        c.setStatusMessage("Request processed.");
                    } else {
                        c.setStatusMessage(HITL_STATUS_TEXT);
                    }
                }));

                em.complete();
            } catch (Throwable t) {
                Log.error("answer() failed", t);
                em.emit(ModelWorkflowChatMessageDTO.snapshot(content, c -> c.setErrorMessage("Failed: " + t.getMessage())));
                em.complete();
            }
        });
    }


    @Transactional
    public String getContext(String keycloakId, Long workflowId, ModelWorkflowChatMessageDTO dto, String message, MultiEmitter<? super ModelWorkflowChatMessageDTO> em) {
        List<ModelWorkflowChatMessageDTO> states = dataAnalysisChatMessageBO.findByDataAnalysis(workflowId, keycloakId);
        String context = "";
        //first user request, second empty message, which will be filled here
        if (states.size() >= 2) {
            states.subList(states.size() - 2, states.size()).clear();
        }
        if (!states.isEmpty()) {
            em.emit(ModelWorkflowChatMessageDTO.snapshot(dto, c -> c.setStatusMessage("Starting to summarize your history...")));

            ToolDTO toolDTO = new ToolDTO("CONTEXT");
            toolDTO.setInput("Your previous messages and the agent's responses");
            em.emit(ModelWorkflowChatMessageDTO.snapshot(dto, c -> c.addTool(toolDTO)));
            context = requestClassifierBot.summarize(message, states);
            if (StringUtils.isEmpty(context) || context.equalsIgnoreCase("null")) {
                context = "";
            }
            toolDTO.setStop();
            toolDTO.addContent("Context:" + context);
            em.emit(ModelWorkflowChatMessageDTO.snapshot(dto, c -> c.updateTool(toolDTO)));

        }
        return context;
    }
}
