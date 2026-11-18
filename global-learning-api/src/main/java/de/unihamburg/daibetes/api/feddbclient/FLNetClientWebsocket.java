package de.unihamburg.daibetes.api.feddbclient;

import bio.cosy.feddb.core.api.socket.FedDBClientDataDTO;
import bio.cosy.feddb.core.api.socket.FedDBClientTypeEnum;
import de.unihamburg.daibetes.api.observer.FLNetClientObserverEmitter;
import de.unihamburg.daibetes.api.observer.FLNetClientObserverEventDTO;
import io.quarkus.arc.log.LoggerName;
import io.quarkus.logging.Log;
import de.unihamburg.daibetes.api.query.response.QueryResponseBO;
import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.websockets.next.*;
import jakarta.inject.Inject;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.jboss.logging.Logger;

@Authenticated // FL-Net clients log in with their platform account
@WebSocket(path = "/query/clients")
public class FLNetClientWebsocket {
    @Inject
    FLNetClientWebsocketHandlerBO handlerBO;

    @Inject
    FLNetClientObserverEmitter observerEmitter;

    @Inject
    QueryResponseBO queryResponseBO;

    @Inject
    SecurityIdentity identity;

    @LoggerName("FLNetClientWebsocket")
    Logger logger;

    @OnOpen
    public void onOpen(WebSocketConnection connection) {
        logger.info("Connection opened: " + connection.id());
        queryResponseBO.putClientName(connection.id(), nameOf(identity));
        observerEmitter.emit(FLNetClientObserverEventDTO.connected(connection.id()));
    }

    @OnClose
    public void onClose(WebSocketConnection connection) {
        handlerBO.removeSystem(connection.id());
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


    // full name of the platform account the client logs in with, else its user name
    private static String nameOf(SecurityIdentity identity) {
        if (identity == null || identity.isAnonymous()) {
            return null;
        }
        if (identity.getPrincipal() instanceof JsonWebToken token && token.getClaim("name") != null) {
            return token.getClaim("name").toString();
        }
        return identity.getPrincipal().getName();
    }
}
