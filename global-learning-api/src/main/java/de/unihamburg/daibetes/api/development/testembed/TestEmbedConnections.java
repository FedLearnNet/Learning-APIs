package de.unihamburg.daibetes.api.development.testembed;

import bio.cosy.feddb.core.execution.run.types.AppConnectionTypesEnum;
import io.quarkus.logging.Log;
import io.quarkus.websockets.next.OpenConnections;
import io.quarkus.websockets.next.WebSocketConnection;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.stream.Stream;

@ApplicationScoped
public class TestEmbedConnections {

    @Inject
    OpenConnections connections;

    public Stream<WebSocketConnection> of(Long appId, AppConnectionTypesEnum type) {
        return connections.listAll().stream()
                .filter(connection -> TestEmbedService.class.getName().equals(connection.endpointId()))
                .filter(connection -> appId.toString().equalsIgnoreCase(connection.pathParam("appid")))
                .filter(connection -> type.isEqual(connection.pathParam("type")));
    }

    public void send(Long appId, AppConnectionTypesEnum type, Object message) {
        of(appId, type).forEach(connection -> connection.sendText(message).subscribe().with(
                sent -> {
                },
                failure -> Log.debugf(failure, "Could not send to testembed connection %s", connection.id())));
    }
}
