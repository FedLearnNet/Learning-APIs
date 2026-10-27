package bio.cosy.feddb.local.execution.attempt;

import bio.cosy.feddb.core.execution.attempt.BaseAttemptLifecycle;
import bio.cosy.feddb.core.execution.run.dto.AppMessageWrapperDTO;
import bio.cosy.feddb.core.execution.run.dto.FinishRunDTO;
import bio.cosy.feddb.core.execution.run.types.AppRunTypeEnum;
import bio.cosy.feddb.core.execution.server.BaseAttemptAppServer;
import bio.cosy.feddb.core.security.Scope;
import bio.cosy.feddb.core.security.ToolApiKeyAuthenticationMechanism;
import bio.cosy.feddb.core.security.ToolAuthenticated;
import bio.cosy.feddb.local.api.importer.run.ConnectorAppRunBO;
import io.quarkus.logging.Log;
import io.quarkus.websockets.next.*;
import io.smallrye.common.annotation.Blocking;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;

// mode = tool or learning, see ExecutionAttemptLauncher; runId is the attempt the app runs; type = AppConnectionTypesEnum.
@WebSocket(path = "/execution/{mode}/{runId}/{type}")
@ToolAuthenticated({Scope.TOOL_RUN, Scope.LEARNING_RUN})
@RolesAllowed(ToolApiKeyAuthenticationMechanism.ROLE)
public class ExecutionAttemptWebsocketService extends BaseAttemptAppServer {

    @Inject
    WebSocketConnection connection;

    @Inject
    ExecutionAttemptLifecycle lifecycle;

    @Inject
    ConnectorAppRunBO connectorAppRunBO;

    @OnOpen
    @Blocking
    public Uni<Void> onOpen(WebSocketConnection connection) {
        if (lifecycle.startsOnConnectTransactional(attemptId(connection))) {
            return open(connection);
        }
        Log.infof("App of attempt %d connected, it waits to be started", attemptId(connection));
        lifecycle.markConnectedTransactional(attemptId(connection));
        return Uni.createFrom().voidItem();
    }

    // An app gone before it finished failed, unless it is started later and may connect again.
    @OnClose
    public void onClose(WebSocketConnection connection) {
        if (lifecycle.startsOnConnectTransactional(attemptId(connection))) {
            closed(connection);
        }
    }

    @OnError
    public void onError(WebSocketConnection connection, Throwable throwable) {
        Log.warnf("Connection of attempt %d failed: %s", attemptId(connection), throwable.getMessage());
        onClose(connection);
    }

    @OnTextMessage
    <T> void onMessage(AppMessageWrapperDTO<T> message) {
        consume(connection, message);
    }

    // A transformer session answers batch after batch, a finished batch neither ends its app nor its connection.
    @Override
    protected void finishRun(Long attemptId, FinishRunDTO finish, AppRunTypeEnum runType) {
        if (!connectorAppRunBO.isSessionAttempt(attemptId)) {
            super.finishRun(attemptId, finish, runType);
        }
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
