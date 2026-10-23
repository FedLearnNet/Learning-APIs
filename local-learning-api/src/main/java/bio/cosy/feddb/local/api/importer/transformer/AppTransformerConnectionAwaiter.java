package bio.cosy.feddb.local.api.importer.transformer;

import bio.cosy.feddb.core.execution.helper.ExecutionSessionWaiter;
import bio.cosy.feddb.core.execution.run.types.RunStatusTypes;
import bio.cosy.feddb.local.api.importer.run.execution.ConnectorRunExecutionWebsocketService;
import bio.cosy.feddb.local.api.importer.run.step.ConnectorRunStepBO;
import io.quarkus.logging.Log;
import io.quarkus.websockets.next.OpenConnections;
import io.quarkus.websockets.next.WebSocketConnection;
import io.smallrye.mutiny.TimeoutException;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

import static bio.cosy.feddb.local.api.importer.transformer.AppTransformerSessionBO.AppTransformerSessionException;

/** Resolves transformer connections while keeping websocket and step concerns out of the core waiter. */
@ApplicationScoped
public class AppTransformerConnectionAwaiter {

    private static final Duration CONNECT_TIMEOUT = Duration.ofMinutes(2);
    private static final Duration RECONNECT_TIMEOUT = Duration.ofSeconds(60);
    private static final Duration POLL_INTERVAL = Duration.ofMillis(250);

    private final ExecutionSessionWaiter waiter;
    private final OpenConnections connections;
    private final ConnectorRunStepBO stepBO;

    public AppTransformerConnectionAwaiter(final ExecutionSessionWaiter waiter,
                                           final OpenConnections connections,
                                           final ConnectorRunStepBO stepBO) {
        this.waiter = waiter;
        this.connections = connections;
        this.stepBO = stepBO;
    }

    /** Waits for the initial websocket handshake after starting the container. */
    public Uni<WebSocketConnection> awaitConnection(final Long stepId, final ConnectorTransformerDTO transformer) {
        return awaitConnection(stepId, transformer, CONNECT_TIMEOUT, "connect");
    }

    /** Allows a temporarily disconnected app time to reconnect before the next batch. */
    public Uni<WebSocketConnection> awaitReconnect(final Long stepId, final ConnectorTransformerDTO transformer) {
        return awaitConnection(stepId, transformer, RECONNECT_TIMEOUT, "reconnect");
    }

    private Uni<WebSocketConnection> awaitConnection(final Long stepId,
                                                     final ConnectorTransformerDTO transformer,
                                                     final Duration timeout,
                                                     final String operation) {
        return Uni.createFrom().deferred(() -> {
            Log.debugf("Waiting up to %ds for app transformer '%s' (step %d) to %s",
                    timeout.toSeconds(), transformer.getAppImage(), stepId, operation);
            return waiter.await(() -> connectionOrFail(stepId, transformer), Optional::isPresent, timeout, POLL_INTERVAL)
                    .map(Optional::orElseThrow)
                    .onFailure(TimeoutException.class).transform(cause -> new AppTransformerSessionException(
                            "App transformer '" + transformer.getAppImage() + "' did not " + operation + " within "
                                    + timeout.toSeconds() + "s. Check the container logs and websocket API-key authentication.",
                            cause));
        });
    }

    private Optional<WebSocketConnection> connectionOrFail(final Long stepId,
                                                          final ConnectorTransformerDTO transformer) {
        RunStatusTypes status = stepBO.statusOf(stepId);
        if (RunStatusTypes.isFinalStatus(status)) {
            throw new AppTransformerSessionException("App transformer '" + transformer.getAppImage()
                    + "' ended as " + status + " before it could take any work");
        }
        return connections.findByEndpointId(ConnectorRunExecutionWebsocketService.class.getName()).stream()
                .filter(connection -> !connection.isClosed())
                .filter(connection -> Objects.equals(stepId, runIdOf(connection)))
                .findFirst();
    }

    private static Long runIdOf(final WebSocketConnection connection) {
        try {
            return Long.valueOf(connection.pathParam("runId"));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
