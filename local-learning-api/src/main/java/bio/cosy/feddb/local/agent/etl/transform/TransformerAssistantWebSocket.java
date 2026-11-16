package bio.cosy.feddb.local.agent.etl.transform;

import bio.cosy.feddb.local.agent.etl.transform.assistant.TransformerAssistantMessageDTO;
import bio.cosy.feddb.local.api.auth.UserIdentity;
import io.quarkus.logging.Log;
import io.quarkus.security.Authenticated;
import io.quarkus.websockets.next.OnClose;
import io.quarkus.websockets.next.OnTextMessage;
import io.quarkus.websockets.next.OpenConnections;
import io.quarkus.websockets.next.WebSocket;
import io.quarkus.websockets.next.WebSocketConnection;
import io.quarkus.websockets.next.WebSocketSecurity;
import jakarta.inject.Inject;

import static bio.cosy.feddb.local.agent.etl.transform.assistant.TransformerAssistantMessageType.APPLIED;

@Authenticated
@WebSocket(path = "/connectors/transformer-assistant")
public class TransformerAssistantWebSocket {

    @Inject
    TransformerAgentStarter agentStarter;

    @Inject
    OpenConnections openConnections;

    @Inject
    UserIdentity userIdentity;

    @Inject
    WebSocketSecurity webSocketSecurity;

    @OnTextMessage
    public void onMessage(TransformerAssistantMessageDTO message, WebSocketConnection connection) {
        String sessionId = connection.id();
        switch (message.getType()) {
            case START -> agentStarter.start(sessionId, message.getConnector(), userIdentity.getKeycloakId(),
                    event -> send(sessionId, event));
            case USER_MESSAGE -> agentStarter.message(sessionId, message);
            case STOP -> agentStarter.stop(sessionId);
            // A run that builds custom code outlasts the access token the connection was opened with
            case TOKEN -> webSocketSecurity.updateSecurityIdentity(message.getMessage())
                    .exceptionally(error -> {
                        Log.warnf("Could not refresh the token of transformer assistant session %s: %s", sessionId,
                                error.getMessage());
                        return null;
                    });
            case APPLY -> {
                TransformerAssistantMessageDTO applied = new TransformerAssistantMessageDTO(APPLIED);
                applied.setConnector(agentStarter.apply(sessionId, message.getSchemaIds()));
                send(sessionId, applied);
            }
            default -> Log.debugf("Ignoring %s sent by the client", message.getType());
        }
    }

    @OnClose
    public void onClose(WebSocketConnection connection) {
        agentStarter.close(connection.id());
    }

    private void send(String sessionId, TransformerAssistantMessageDTO message) {
        openConnections.findByConnectionId(sessionId).ifPresent(connection -> connection.sendTextAndAwait(message));
    }
}
