package de.unihamburg.daibetes.api.workflow.chat;

import io.quarkus.websockets.next.*;
import io.smallrye.mutiny.Multi;
import jakarta.enterprise.context.SessionScoped;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;

@SessionScoped
@WebSocket(path = "workflow/{workflowId}/chat")
public class WorkflowChatService {

    @Inject
    WebSocketConnection connection;

    @Inject
    WorkflowChatBO workflowChatBO;

    @OnOpen
    public void onOpen() {
        // optional logging, auth etc.
    }

    @OnClose
    public void onClose() {
        // optional cleanup
    }

    @OnTextMessage
    @ActivateRequestContext
    public Multi<WorkflowChatMessageDTO> onMessage(String message) {
        String wf = connection.pathParam("workflowId");
        Long workflowId = Long.parseLong(wf);
        return workflowChatBO.processMessage(message, workflowId);
    }
}
