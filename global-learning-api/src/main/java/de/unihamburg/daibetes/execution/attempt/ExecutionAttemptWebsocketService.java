package de.unihamburg.daibetes.execution.attempt;

import bio.cosy.feddb.core.execution.attempt.BaseAttemptLifecycle;
import bio.cosy.feddb.core.execution.run.dto.AppMessageWrapperDTO;
import bio.cosy.feddb.core.execution.server.BaseAttemptAppServer;
import bio.cosy.feddb.core.security.Scope;
import bio.cosy.feddb.core.security.ToolApiKeyAuthenticationMechanism;
import bio.cosy.feddb.core.security.ToolAuthenticated;
import io.quarkus.websockets.next.*;
import io.smallrye.common.annotation.Blocking;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;

// type = AppConnectionTypesEnum; runId is the attempt the app runs: an analysis or a training on the platform.
@WebSocket(path = "/execution/{mode}/{runId}/{type}")
@ToolAuthenticated({Scope.MODEL_PREDICTION_RUN, Scope.MODEL_WORKFLOW_RUN})
@RolesAllowed(ToolApiKeyAuthenticationMechanism.ROLE)
public class ExecutionAttemptWebsocketService extends BaseAttemptAppServer {

    @Inject
    WebSocketConnection connection;

    @Inject
    ExecutionAttemptLifecycle lifecycle;

    @OnOpen
    @Blocking
    public Uni<Void> onOpen(WebSocketConnection connection) {
        return sendStart(connection);
    }

    // The key is revoked once the attempt ends. An app gone before it finished failed.
    @OnClose
    public void onClose(WebSocketConnection connection) {
        failUnfinishedAttempt(connection);
    }

    @OnError
    public void onError(WebSocketConnection connection, Throwable throwable) {
        failUnfinishedAttempt(connection);
    }

    @OnTextMessage
    <T> void onMessage(AppMessageWrapperDTO<T> message) {
        consume(connection, message);
    }

    @Override
    protected BaseAttemptLifecycle<?, ?, ?, ?> lifecycle() {
        return lifecycle;
    }

    @Override
    protected WebSocketConnection connection() {
        return connection;
    }
}
