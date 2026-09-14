package de.unihamburg.daibetes.api.analysis.chat;

import bio.cosy.feddb.core.api.model.workflow.chat.DataAnalysisChatWrapperDTO;
import bio.cosy.feddb.core.api.model.workflow.chat.ModelWorkflowChatMessageDTO;
import bio.cosy.feddb.core.api.model.workflow.chat.ModelWorkflowChatMessageType;
import de.unihamburg.daibetes.api.analysis.chat.hitl.HumanInTheLoopRequestBO;
import de.unihamburg.daibetes.api.analysis.chat.hitl.HumanInTheLoopRequestDTO;
import de.unihamburg.daibetes.api.auth.UserIdentity;
import io.quarkus.websockets.next.*;
import jakarta.enterprise.context.SessionScoped;
import jakarta.inject.Inject;

import java.util.Optional;

@SessionScoped
@WebSocket(path = "/model/workflow/{workflowId}/chat")
public class DataAnalysisChatService {

    @Inject
    WebSocketConnection connection;

    @Inject
    DataAnalysisChatMessageBO modelWorkflowChatMessageBO;

    @Inject
    HumanInTheLoopRequestBO humanInTheLoopRequestBO;

    @Inject
    UserIdentity userIdentity;

    @OnOpen
    public void onOpen() {
        // optional logging, auth etc.
    }

    @OnClose
    public void onClose() {
        // optional cleanup
    }

    @OnTextMessage
    public DataAnalysisChatWrapperDTO<ModelWorkflowChatMessageDTO> onMessage(String message) {
        String keycloakId = userIdentity.getKeycloakIdOrElse("test");

        String wf = connection.pathParam("workflowId");
        Long workflowId = Long.parseLong(wf);

        Optional<HumanInTheLoopRequestDTO> hitl = humanInTheLoopRequestBO.findPendingByDataAnalysisTransactional(workflowId, keycloakId);
        ModelWorkflowChatMessageDTO createdMessage = null;
        if (hitl.isEmpty()) {
            createdMessage = modelWorkflowChatMessageBO.create(message, workflowId);
        }
        modelWorkflowChatMessageBO.processMessageAsync(keycloakId, message, workflowId);
        DataAnalysisChatWrapperDTO<ModelWorkflowChatMessageDTO> wrapper = new DataAnalysisChatWrapperDTO<ModelWorkflowChatMessageDTO>();
        wrapper.setType(ModelWorkflowChatMessageType.CHAT_MESSAGE);

        if (hitl.isEmpty()) {
            wrapper.setMessage(createdMessage);
        } else {
            createdMessage = modelWorkflowChatMessageBO.getByIdTransactional(hitl.get().getMessageId());
            createdMessage.addHumanInTheLoopAnswer(message);
            wrapper.setMessage(createdMessage);
        }
        return wrapper;

    }
}
