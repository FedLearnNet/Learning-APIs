package de.unihamburg.daibetes.api.analysis.chat;

import bio.cosy.feddb.core.api.model.chat.ToolDTO;
import bio.cosy.feddb.core.api.model.workflow.chat.DataAnalysisChatWrapperDTO;
import bio.cosy.feddb.core.api.model.workflow.chat.ModelWorkflowChatMessageDTO;
import bio.cosy.feddb.core.api.model.workflow.chat.ModelWorkflowChatMessageType;
import bio.cosy.feddb.core.base.BaseBo;
import de.unihamburg.daibetes.agent.anlysis.ModelWorkflowAgent;
import de.unihamburg.daibetes.api.analysis.chat.hitl.HumanInTheLoopRequestBO;
import de.unihamburg.daibetes.api.analysis.chat.hitl.HumanInTheLoopRequestDTO;
import io.quarkus.logging.Log;
import io.quarkus.websockets.next.OpenConnections;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.context.ManagedExecutor;

import java.util.List;
import java.util.Optional;


@ApplicationScoped
public class DataAnalysisChatMessageBO extends BaseBo<ModelWorkflowChatMessageDTO, DataAnalysisChatMessageEntity, DataAnalysisChatMessageAO, DataAnalysisChatMessageMapper> {
    @Inject
    OpenConnections connections;

    @Inject
    ModelWorkflowAgent modelWorkflowAgent;

    @Inject
    ManagedExecutor executor;

    @Inject
    HumanInTheLoopRequestBO humanInTheLoopRequestBO;

    public List<ModelWorkflowChatMessageDTO> findByDataAnalysis(Long id, String keycloakId) {
        return mapper.entitiesToDtos(ao.findByDataAnalysis(id, keycloakId));
    }

    public ModelWorkflowChatMessageDTO create(String message, Long workflowId) {
        return create(message, workflowId, true);
    }

    public void processMessage(String message, Long workflowId, String keycloakId) {
        Optional<HumanInTheLoopRequestDTO> hitl = humanInTheLoopRequestBO.findPendingByDataAnalysisTransactional(workflowId, keycloakId);
        ModelWorkflowChatMessageDTO created = null;
        if (hitl.isPresent()) {
            Log.debugf("There is a pending HITL request for workflow %s, skipping automated processing", workflowId);
            created = getByIdTransactional(hitl.get().getMessageId());
        } else {
            created = create("", workflowId, false);
        }
        modelWorkflowAgent.answer(workflowId, created, message, keycloakId)
                .onItem().invoke(this::updateAndNotify)
                .runSubscriptionOn(Infrastructure.getDefaultWorkerPool())
                .subscribe().with(
                        item -> {
                            Log.debugf("Processing Message [%s] [%s]", message, item.getId());
                        },
                        failure -> Log.error(
                                String.format("Failed to process message '%s' for workflow %s", message, workflowId),
                                failure
                        ),
                        () -> Log.debugf("Finished processing message '%s' for workflow %s", message, workflowId)
                );
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public ModelWorkflowChatMessageDTO create(String message, Long workflowId, boolean request) {
        ModelWorkflowChatMessageDTO dto = new ModelWorkflowChatMessageDTO();
        dto.setMessage(message);
        dto.setRequest(request);
        dto.setWorkflowId(workflowId);
        return create(dto);
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public ModelWorkflowChatMessageDTO getByIdTransactional(Long id) {
        return getById(id);
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void updateAndNotify(ModelWorkflowChatMessageDTO message) {
        Long workflowId = message.getWorkflowId();
        if (workflowId == null) {
            Log.errorf("Workflow ID is null for message: %s", message);
            return;
        }
        DataAnalysisChatMessageEntity entity = ao.findByIdOptional(message.getId())
                .orElseThrow(() -> new IllegalStateException("Message not found for ID: " + message.getId()));
        entity.setStatusMessage(message.getStatusMessage());
        entity.setTools(mergeTools(entity.getTools(), message.getTools()));
        entity.setReasonings(message.getReasonings());
        entity.setDone(message.isDone());
        entity.setMessage(message.getMessage());
        entity.setErrorMessage(message.getErrorMessage());
        entity.setAction(message.getAction());
        ao.persist(entity);
        sendToClient(mapper.entityToDto(entity), workflowId);
    }


    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void sendTool(ToolDTO toolDTO, String sessionId) {
        try {
            Long id = Long.parseLong(sessionId);
            DataAnalysisChatMessageEntity entity = ao.findByIdOptional(id)
                    .orElseThrow(() -> new IllegalStateException("Message not found for ID: " + id));
            entity.setTools(mergeTools(entity.getTools(), toolDTO));
            ao.persist(entity);
            sendToClient(mapper.entityToDto(entity), id);
        } catch (NumberFormatException e) {
            Log.errorf("Invalid session ID format: %s for persisting tool update", sessionId);
        }
    }


    @ActivateRequestContext
    public void processMessageAsync(String keycloakId, String message, Long workflowId) {
        executor.execute(() -> {
            try {
                processMessage(message, workflowId, keycloakId);
            } catch (Exception e) {
                Log.errorf(e, "Failed to process message '%s' for workflow %s", message, workflowId);
            }
        });
    }

    private void sendToClient(ModelWorkflowChatMessageDTO message, Long workflowId) {
        DataAnalysisChatWrapperDTO<ModelWorkflowChatMessageDTO> wrapper = new DataAnalysisChatWrapperDTO<ModelWorkflowChatMessageDTO>();
        wrapper.setType(ModelWorkflowChatMessageType.CHAT_MESSAGE);
        wrapper.setMessage(message);
        sendToClient(wrapper, workflowId);
    }

    private void sendToClient(DataAnalysisChatWrapperDTO message, Long workflowId) {

        Log.debugf("Sending message to model workflow %s: %s", workflowId, message);
        connections.findByEndpointId(DataAnalysisChatService.class.getName()).forEach(c -> {
            Long wId = Long.parseLong(c.pathParam("workflowId"));
            if (wId.equals(workflowId)) {
                try {
                    c.sendTextAndAwait(message);
                } catch (Exception e) {
                    Log.errorf("Failed to send message to connection %s: %s", c.id(), e.getMessage());
                }
            }
        });

    }

    private List<ToolDTO> mergeTools(List<ToolDTO> existingTools, ToolDTO newTool) {
        if (existingTools == null || existingTools.isEmpty()) {
            return List.of(newTool);
        }
        for (int i = 0; i < existingTools.size(); i++) {
            ToolDTO tool = existingTools.get(i);
            if (tool.getId().equals(newTool.getId())) {
                existingTools.set(i, newTool);
                return existingTools;
            }
        }
        existingTools.add(newTool);
        return existingTools;
    }

    private List<ToolDTO> mergeTools(List<ToolDTO> existingTools, List<ToolDTO> newTools) {
        if (existingTools == null || existingTools.isEmpty()) {
            return newTools;
        }
        for (ToolDTO newTool : newTools) {
            existingTools = mergeTools(existingTools, newTool);
        }
        return existingTools;
    }
}
