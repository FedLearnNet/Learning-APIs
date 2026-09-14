package de.unihamburg.daibetes.api.feddbclient;

import bio.cosy.feddb.core.api.socket.FedDBClientDataDTO;
import de.unihamburg.daibetes.api.observer.FLNetClientObserverEmitter;
import de.unihamburg.daibetes.api.observer.FLNetClientObserverEventDTO;
import io.quarkus.arc.log.LoggerName;
import io.quarkus.logging.Log;
import io.quarkus.websockets.next.*;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

@WebSocket(path = "/query/clients")
public class FLNetClientWebsocket {
    private static final CloseReason UNAUTHORIZED = new CloseReason(1008, "Authentication required");

    @Inject
    FLNetClientWebsocketHandlerBO handlerBO;

    @Inject
    FLNetClientObserverEmitter observerEmitter;

    @Inject
    FLNetClientAuthentication authentication;

    @LoggerName("FLNetClientWebsocket")
    Logger logger;

    @OnOpen
    public void onOpen(WebSocketConnection connection) {
        if (!isConnectionAuthorized(connection)) {
            return;
        }
        logger.info("Connection opened: " + connection.id());
        observerEmitter.emit(FLNetClientObserverEventDTO.connected(connection.id()));
    }

    @OnClose
    public void onClose(WebSocketConnection connection) {
        logger.info("Connection closed: " + connection.id());
        observerEmitter.emit(FLNetClientObserverEventDTO.disconnected(connection.id()));
    }

    @OnError
    public void onError(WebSocketConnection connection, Throwable throwable) {
        logger.error("Error in FLNetClientWebsocket: " + throwable.getMessage());
    }

    @OnTextMessage
    public <T> void onMessage(FedDBClientDataDTO<T> m, WebSocketConnection connection) {
        Log.info("Received message from " + connection.id() + ": " + m.getMessageType());
        if (!isConnectionAuthorized(connection)) {
            return;
        }
        observerEmitter.emit(FLNetClientObserverEventDTO.received(
                connection.id(),
                m.getMessageType() != null ? m.getMessageType().name() : "UNKNOWN",
                m.getMessage()
        ));
        try {
            handlerBO.handle(m, connection.id());
        } catch (Exception e) {
            logger.error("Error handling message from " + connection.id() + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    private boolean isConnectionAuthorized(WebSocketConnection connection) {
        if (authentication.isAuthorized()) {
            return true;
        }
        logger.warn("Closing unauthenticated FLNet client websocket connection: " + connection.id());
        connection.closeAndAwait(UNAUTHORIZED);
        return false;
    }
}
