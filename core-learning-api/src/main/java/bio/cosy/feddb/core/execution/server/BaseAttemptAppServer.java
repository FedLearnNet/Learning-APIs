package bio.cosy.feddb.core.execution.server;

import bio.cosy.feddb.core.execution.attempt.BaseAttemptLifecycle;
import bio.cosy.feddb.core.execution.message.ConsoleStdOutDTO;
import bio.cosy.feddb.core.execution.message.ExecutionMessageLogDTO;
import bio.cosy.feddb.core.execution.message.ExecutionMessageMetricDTO;
import bio.cosy.feddb.core.execution.run.dto.*;
import bio.cosy.feddb.core.execution.run.types.AppConnectionTypesEnum;
import bio.cosy.feddb.core.execution.run.types.AppMessageTypeEnum;
import bio.cosy.feddb.core.execution.run.types.AppRunTypeEnum;
import bio.cosy.feddb.core.execution.run.types.RunStatusTypes;
import io.quarkus.logging.Log;
import io.quarkus.websockets.next.CloseReason;
import io.quarkus.websockets.next.WebSocketConnection;
import io.smallrye.mutiny.Uni;

public abstract class BaseAttemptAppServer extends BaseAppServer {

    private static final CloseReason RUN_FINISHED = new CloseReason(1000, "Run finished successfully");

    protected abstract BaseAttemptLifecycle<?, ?, ?, ?> lifecycle();

    protected abstract WebSocketConnection connection();

    protected AppMessageWrapperDTO<StartRunDTO> startMessageOf(StartRunDTO start) {
        return new AppMessageWrapperDTO<>(AppMessageTypeEnum.START_PREDICTION, start, AppRunTypeEnum.MODEL_RUN);
    }

    /**
     * Sends the app its start, without waiting for the write; nothing to an app connecting for an attempt that
     * ended already, an empty message makes it close. Reads the attempt, so the endpoint calls it blocking.
     */
    protected Uni<Void> open(WebSocketConnection connection) {
        Long attemptId = attemptId(connection);
        Log.infof("App connected for attempt %d", attemptId);
        if (lifecycle().checkIfEndedTransactional(attemptId)) {
            return Uni.createFrom().voidItem();
        }
        AppMessageWrapperDTO<StartRunDTO> start = startMessageOf(lifecycle().startupOf(attemptId));
        if (start == null) {
            return Uni.createFrom().voidItem();
        }
        return connection.sendText(start)
                .onFailure().invoke(e -> Log.errorf(e, "Could not send attempt %d its start", attemptId));
    }

    // An app gone before it finished failed; ignored for an attempt that ended already.
    protected void closed(WebSocketConnection connection) {
        Long attemptId = attemptId(connection);
        try {
            UpdateRunDTO closed = new UpdateRunDTO();
            closed.setStatus(RunStatusTypes.ERROR);
            closed.setError("App closed its connection before it finished");
            lifecycle().update(attemptId, closed);
        } catch (RuntimeException e) {
            Log.errorf(e, "Could not close attempt %d", attemptId);
        }
    }

    protected <T> void consume(WebSocketConnection connection, AppMessageWrapperDTO<T> message) {
        String type = connection.pathParam("type");
        if (!AppConnectionTypesEnum.APP.isEqual(type)) {
            Log.errorf("Ignoring message of unsupported connection type %s", type);
            return;
        }
        try {
            handleAppMessages(message, attemptId(connection), message.getRunType());
        } catch (RuntimeException e) {
            Log.errorf(e, "Could not handle %s of attempt %s", message.getType(), connection.pathParam("runId"));
        }
    }

    // Metrics and the lifecycle messages of a federated participant on top of what every app reports.
    @Override
    protected <T> void handleAppMessages(AppMessageWrapperDTO<T> m, Long attemptId, AppRunTypeEnum runType) {
        switch (m.getType()) {
            case LOG_METRIC -> lifecycle().metric(attemptId, decode(m.getMessage(), ExecutionMessageMetricDTO.class));
            case UPDATE_FEDERATED_RUN -> {
                FederatedRunLifecycleDTO federated = decode(m.getMessage(), FederatedRunLifecycleDTO.class);
                if (federated.toRunStatus() != null) {
                    UpdateRunDTO update = new UpdateRunDTO();
                    update.setStatus(federated.toRunStatus());
                    update.setError(federated.getError());
                    updateRun(attemptId, update, runType);
                }
            }
            case FINISH_FEDERATED_RUN -> {
                FederatedRunLifecycleDTO federated = decode(m.getMessage(), FederatedRunLifecycleDTO.class);
                FinishRunDTO finish = new FinishRunDTO();
                finish.setStatus(federated.toRunStatus());
                finish.setError(federated.getError());
                finishRun(attemptId, finish, runType);
            }
            default -> super.handleAppMessages(m, attemptId, runType);
        }
    }

    @Override
    protected void startRun(Long attemptId, StartRunDTO startRun, AppRunTypeEnum runType) {
        lifecycle().markRunning(attemptId);
    }

    @Override
    protected void updateRun(Long attemptId, UpdateRunDTO update, AppRunTypeEnum runType) {
        lifecycle().update(attemptId, update);
    }

    @Override
    protected void finishRun(Long attemptId, FinishRunDTO finish, AppRunTypeEnum runType) {
        lifecycle().finish(attemptId, finish);
        WebSocketConnection connection = connection();
        try {
            if (connection != null && !connection.isClosed()) {
                connection.closeAndAwait(RUN_FINISHED);
            }
        } catch (RuntimeException e) {
            Log.debugf("Could not close the connection of attempt %d: %s", attemptId, e.getMessage());
        }
    }

    @Override
    protected void logMessage(Long attemptId, ExecutionMessageLogDTO log, AppRunTypeEnum runType) {
        lifecycle().log(attemptId, log);
    }

    // Console output is kept as a log of its own kind.
    @Override
    protected void consoleMessage(Long attemptId, ConsoleStdOutDTO console, AppRunTypeEnum runType) {
        ExecutionMessageLogDTO log = new ExecutionMessageLogDTO();
        log.setSeverity("CONSOLE");
        log.setMessage(console.getMsg());
        lifecycle().log(attemptId, log);
    }

    protected Long attemptId(WebSocketConnection connection) {
        return Long.parseLong(connection.pathParam("runId"));
    }
}
