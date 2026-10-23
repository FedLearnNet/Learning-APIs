package de.unihamburg.daibetes.api.analysis.run;

import bio.cosy.feddb.core.execution.message.ConsoleStdOutDTO;
import bio.cosy.feddb.core.execution.message.ExecutionMessageLogDTO;
import bio.cosy.feddb.core.execution.run.dto.AppMessageWrapperDTO;
import bio.cosy.feddb.core.execution.run.dto.FinishRunDTO;
import bio.cosy.feddb.core.execution.run.dto.StartRunDTO;
import bio.cosy.feddb.core.execution.run.dto.UpdateRunDTO;
import bio.cosy.feddb.core.execution.run.types.AppConnectionTypesEnum;
import bio.cosy.feddb.core.execution.run.types.AppMessageTypeEnum;
import bio.cosy.feddb.core.execution.run.types.AppRunTypeEnum;
import bio.cosy.feddb.core.execution.run.types.RunStatusTypes;
import bio.cosy.feddb.core.execution.server.ModelAppServer;
import bio.cosy.feddb.core.security.Scope;
import bio.cosy.feddb.core.security.ToolApiKeyAuthenticationMechanism;
import bio.cosy.feddb.core.security.ToolAuthenticated;
import de.unihamburg.daibetes.execution.attempt.ExecutionAttemptAO;
import de.unihamburg.daibetes.execution.attempt.ExecutionAttemptEntity;
import de.unihamburg.daibetes.execution.attempt.ExecutionAttemptLifecycle;
import io.quarkus.logging.Log;
import io.quarkus.websockets.next.*;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;

// type = AppConnectionTypesEnum; runId is the attempt the app runs, single apps and workflows alike.
@WebSocket(path = "/model/run/{mode}/{runId}/{type}")
@ToolAuthenticated({Scope.MODEL_PREDICTION_RUN, Scope.MODEL_WORKFLOW_RUN})
@RolesAllowed(ToolApiKeyAuthenticationMechanism.ROLE)
public class DataAnalysisRunWebsocketService extends ModelAppServer {

    private static final CloseReason RUN_FINISHED =
            new CloseReason(1000, "Run finished successfully");

    @Inject
    WebSocketConnection connection;

    @Inject
    ExecutionAttemptAO attemptAO;

    @Inject
    ExecutionAttemptLifecycle lifecycle;

    @Inject
    DataAnalysisRunResultBO resultBO;

    @OnOpen
    public AppMessageWrapperDTO<StartRunDTO> onOpen(WebSocketConnection connection) {
        Log.info("Connection opened: " + connection.id());
        return startTest(attemptId(connection));
    }

    // The key is revoked once the attempt ends. An app gone before it finished failed.
    @OnClose
    public void onClose(WebSocketConnection connection) {
        Log.info("Connection closed: " + connection.id() + " Reason: " + connection.closeReason());
        closedRun(attemptId(connection));
    }

    @OnError
    public void onError(WebSocketConnection connection, Throwable throwable) {
        Log.error("Error in WebsocketClient: " + throwable.getMessage());
        closedRun(attemptId(connection));
    }

    @Transactional
    public AppMessageWrapperDTO<StartRunDTO> startTest(Long attemptId) {
        Log.info("Starting attempt: " + attemptId);
        ExecutionAttemptEntity attempt = attemptAO.findByIdOptional(attemptId)
                .orElseThrow(() -> new NotFoundException("Attempt with id " + attemptId + " not found"));
        if (RunStatusTypes.isFinalStatus(attempt.getStatus())) {
            return new AppMessageWrapperDTO<>();
        }
        StartRunDTO start = resultBO.withInputFileUrls(lifecycle.startupOf(attemptId));
        return new AppMessageWrapperDTO<>(AppMessageTypeEnum.START_PREDICTION, start, AppRunTypeEnum.MODEL_RUN);
    }

    @OnTextMessage
    <T> void consume(AppMessageWrapperDTO<T> m) {
        String runIdString = connection.pathParam("runId");
        String type = connection.pathParam("type"); // has to be a AppConnectionTypesEnum
        try {
            Long runId = Long.parseLong(runIdString);
            Log.debug("Received message Text: " + m.getMessage() + " for container " + runId + " and type " + type);
            if (AppConnectionTypesEnum.APP.isEqual(type)) {
                handleAppMessages(m, runId, m.getRunType());
            } else {
                Log.error("Received message for unknown/not supported type: " + type);
            }
        } catch (Exception e) {
            Log.error("Error handling message for app " + runIdString + " and type " + type, e);
        }
    }

    @Override
    protected void updateRun(Long runId, UpdateRunDTO updateTest, AppRunTypeEnum runType) {
        lifecycle.update(runId, updateTest);
    }

    @Override
    protected void finishRun(Long runId, FinishRunDTO finishTest, AppRunTypeEnum runType) {
        lifecycle.finish(runId, finishTest);
        Log.info("Closing websocket connection for finished run " + runId);
        closeRun();
    }

    public void closedRun(Long attemptId) {
        try {
            UpdateRunDTO closed = new UpdateRunDTO();
            closed.setStatus(RunStatusTypes.ERROR);
            closed.setError("App closed its connection before it finished");
            // Ignored for an attempt that ended already.
            lifecycle.update(attemptId, closed);
        } catch (Exception e) {
            Log.errorf("Error while closing run %d : %s", attemptId, e.getMessage());
        }
    }

    @Override
    protected void startRun(Long runId, StartRunDTO startRun, AppRunTypeEnum runType) {
        lifecycle.markRunning(runId);
    }

    @Override
    protected void logMessage(Long runId, ExecutionMessageLogDTO runMessage, AppRunTypeEnum runType) {
        lifecycle.log(runId, runMessage);
    }

    // Console output is kept as a log of its own kind.
    @Override
    protected void consoleMessage(Long runId, ConsoleStdOutDTO runMessage, AppRunTypeEnum runType) {
        ExecutionMessageLogDTO log = new ExecutionMessageLogDTO();
        log.setSeverity("CONSOLE");
        log.setMessage(runMessage.getMsg());
        lifecycle.log(runId, log);
    }

    private static Long attemptId(WebSocketConnection connection) {
        return Long.parseLong(connection.pathParam("runId"));
    }

    private void closeRun() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.closeAndAwait(RUN_FINISHED);
            }
        } catch (Exception e) {
            Log.debugf("Error while closing connection: %s", e.getMessage());
        }
    }
}
