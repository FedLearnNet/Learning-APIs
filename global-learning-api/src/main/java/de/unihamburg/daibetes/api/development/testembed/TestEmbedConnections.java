package de.unihamburg.daibetes.api.development.testembed;

import bio.cosy.feddb.core.execution.run.types.AppConnectionTypesEnum;
import io.quarkus.logging.Log;
import io.quarkus.websockets.next.OpenConnections;
import io.quarkus.websockets.next.WebSocketConnection;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.nio.channels.ClosedChannelException;
import java.util.stream.Stream;

@ApplicationScoped
public class TestEmbedConnections {

    @Inject
    OpenConnections connections;

    public Stream<WebSocketConnection> of(Long appId, AppConnectionTypesEnum type) {
        return connections.listAll().stream()
                .filter(connection -> !connection.isClosed())
                .filter(connection -> TestEmbedService.class.getName().equals(connection.endpointId()))
                .filter(connection -> appId.toString().equalsIgnoreCase(connection.pathParam("appid")))
                .filter(connection -> type.isEqual(connection.pathParam("type")));
    }

    public void send(Long appId, AppConnectionTypesEnum type, Object message) {
        of(appId, type).forEach(connection -> {
            try {
                connection.sendText(message).subscribe().with(sent -> {},
                        failure -> logSendFailure(connection, failure));
            } catch (RuntimeException failure) {
                logSendFailure(connection, failure);
            }
        });
    }

    // One browser disconnecting must not prevent other recipients or the app's lifecycle update.
    public void sendTo(WebSocketConnection connection, Object message) {
        if (connection.isClosed()) {
            Log.debugf("Skipping closed testembed connection %s", connection.id());
            return;
        }
        try {
            connection.sendTextAndAwait(message);
        } catch (RuntimeException failure) {
            logSendFailure(connection, failure);
        }
    }

    private void logSendFailure(WebSocketConnection connection, Throwable failure) {
        if (isClosedFailure(failure)) {
            Log.debugf("Testembed connection %s closed during send", connection.id());
        } else {
            Log.errorf(failure, "Could not send to testembed connection %s", connection.id());
        }
    }

    static boolean isClosedFailure(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof ClosedChannelException
                    || "WebSocket is closed".equals(cause.getMessage())
                    || "Connection was closed".equals(cause.getMessage())) {
                return true;
            }
        }
        return false;
    }
}
