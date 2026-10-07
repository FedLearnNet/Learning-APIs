package bio.cosy.feddb.local.api.importer.transformer.session;

import bio.cosy.feddb.core.api.run.RunStatusTypes;
import bio.cosy.feddb.local.api.importer.run.execution.ConnectorRunExecutionWebsocketService;
import bio.cosy.feddb.local.api.importer.run.step.ConnectorRunStepBO;
import io.quarkus.logging.Log;
import io.quarkus.websockets.next.OpenConnections;
import io.quarkus.websockets.next.WebSocketConnection;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

@ApplicationScoped
public class AppTransformerConnectionBO {

    private static final Duration CONNECT_TIMEOUT = Duration.ofMinutes(2);
    private static final Duration RECONNECT_TIMEOUT = Duration.ofSeconds(60);
    private static final Duration POLL = Duration.ofMillis(250);
    private static final Duration LOG_INTERVAL = Duration.ofSeconds(15);

    @Inject
    OpenConnections connections;

    @Inject
    ConnectorRunStepBO stepBO;

    public Optional<WebSocketConnection> find(Long stepId) {
        return connections.findByEndpointId(ConnectorRunExecutionWebsocketService.class.getName()).stream()
                .filter(connection -> Objects.equals(stepId, runIdOf(connection)))
                .findFirst();
    }

    // False after the timeout; a started app connects within seconds, so it should fail fast rather than hang
    public boolean awaitConnection(Long stepId, String appImage) {
        Log.infof("Waiting up to %ds for app transformer '%s' (step %d) to connect",
                CONNECT_TIMEOUT.toSeconds(), appImage, stepId);
        Instant started = Instant.now();
        Instant deadline = started.plus(CONNECT_TIMEOUT);
        Instant nextLog = started.plus(LOG_INTERVAL);
        while (Instant.now().isBefore(deadline)) {
            if (find(stepId).isPresent()) {
                return true;
            }
            failIfEnded(stepId, appImage);
            if (Instant.now().isAfter(nextLog)) {
                nextLog = Instant.now().plus(LOG_INTERVAL);
                Log.infof("Still waiting for app transformer '%s' (step %d) after %ds", appImage, stepId,
                        Duration.between(started, Instant.now()).toSeconds());
            }
            sleep("connect");
        }
        return false;
    }

    // The wrapper reconnects on a timer, so between two batches the connection can be gone for a few seconds
    public WebSocketConnection awaitReconnect(Long stepId, String appImage) {
        Optional<WebSocketConnection> connection = find(stepId);
        if (connection.isPresent()) {
            return connection.get();
        }
        Log.infof("App transformer '%s' (step %d) is not connected, waiting up to %ds for it to come back",
                appImage, stepId, RECONNECT_TIMEOUT.toSeconds());
        Instant deadline = Instant.now().plus(RECONNECT_TIMEOUT);
        while (Instant.now().isBefore(deadline)) {
            sleep("reconnect");
            connection = find(stepId);
            if (connection.isPresent()) {
                Log.infof("App transformer '%s' (step %d) reconnected", appImage, stepId);
                return connection.get();
            }
        }
        throw new AppTransformerSessionException("App transformer '" + appImage + "' did not reconnect within "
                + RECONNECT_TIMEOUT.toSeconds() + "s");
    }

    // An app that connected and dropped has already failed its step, so there is nothing left to wait for
    private void failIfEnded(Long stepId, String appImage) {
        RunStatusTypes status = statusOf(stepId);
        if (status != null && RunStatusTypes.isFinalStatus(status)) {
            throw new AppTransformerSessionException("App transformer '" + appImage + "' ended as " + status
                    + " before it could take any work - it connected and dropped, or failed to start.");
        }
    }

    private RunStatusTypes statusOf(Long stepId) {
        try {
            return stepBO.statusOf(stepId);
        } catch (Exception e) {
            return null;
        }
    }

    private static Long runIdOf(WebSocketConnection connection) {
        try {
            return Long.parseLong(connection.pathParam("runId"));
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static void sleep(String waitingFor) {
        try {
            Thread.sleep(POLL);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AppTransformerSessionException("Interrupted while waiting for app transformer to " + waitingFor);
        }
    }
}
