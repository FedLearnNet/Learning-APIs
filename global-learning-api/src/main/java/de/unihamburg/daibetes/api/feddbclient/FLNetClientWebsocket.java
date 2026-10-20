package de.unihamburg.daibetes.api.feddbclient;

import bio.cosy.feddb.core.api.socket.FedDBClientDataDTO;
import bio.cosy.feddb.core.api.socket.FedDBClientTypeEnum;
import de.unihamburg.daibetes.api.observer.FLNetClientObserverEmitter;
import de.unihamburg.daibetes.api.observer.FLNetClientObserverEventDTO;
import io.quarkus.arc.log.LoggerName;
import io.quarkus.logging.Log;
import io.quarkus.security.Authenticated;
import io.quarkus.websockets.next.*;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

@Authenticated // FL-Net clients log in with their platform account
@WebSocket(path = "/query/clients")
public class FLNetClientWebsocket {
    @Inject
    FLNetClientWebsocketHandlerBO handlerBO;

    @Inject
    FLNetClientObserverEmitter observerEmitter;

    @LoggerName("FLNetClientWebsocket")
    Logger logger;

    @OnOpen
    public void onOpen(WebSocketConnection connection) {
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
        observerEmitter.emit(FLNetClientObserverEventDTO.received(
                connection.id(),
                m.getMessageType() != null ? m.getMessageType().name() : "UNKNOWN",
                m.getMessage()
        ));
        try {
            if (FedDBClientTypeEnum.RELAY_CERT.equals(m.getMessageType())) {
                handlerBO.handleRelayCert(m, connection.id());
            } else {
                handlerBO.handle(m, connection.id());
            }
        } catch (Exception e) {
            logger.error("Error handling message from " + connection.id() + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

}
