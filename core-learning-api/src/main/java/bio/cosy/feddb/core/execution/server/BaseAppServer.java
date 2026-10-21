package bio.cosy.feddb.core.execution.server;

import bio.cosy.feddb.core.execution.message.ConsoleStdOutDTO;
import bio.cosy.feddb.core.execution.message.ExecutionMessageLogDTO;
import bio.cosy.feddb.core.execution.run.dto.AppMessageWrapperDTO;
import bio.cosy.feddb.core.execution.run.dto.FinishRunDTO;
import bio.cosy.feddb.core.execution.run.dto.StartRunDTO;
import bio.cosy.feddb.core.execution.run.dto.UpdateRunDTO;
import bio.cosy.feddb.core.execution.run.types.AppRunTypeEnum;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.logging.Log;
import jakarta.inject.Inject;


/**
 * The BaseAppServer class is an abstract class that provides a foundational template
 * for applications requiring websocket communication and processing of various run-related messages.
 * This can be import app, model app prediction, and learning (FC template V2).
 * It defines a set of abstract methods and protected utility methods that can be
 * implemented or extended by subclasses.
 * <p>
 * This class is typically used to handle communication events (e.g., connection open, close, or errors)
 * as well as to process specific application messages such as starting, updating, finishing runs,
 * and logging messages within the context of a configured application.
 */
public abstract class BaseAppServer {

    @Inject
    protected ObjectMapper objectMapper;

    protected abstract void updateRun(Long runId, UpdateRunDTO updateTest, AppRunTypeEnum runType);

    protected abstract void finishRun(Long runId, FinishRunDTO finishTest, AppRunTypeEnum runType);

    protected abstract void startRun(Long runId, StartRunDTO startRun, AppRunTypeEnum runType);

    protected abstract void logMessage(Long runId, ExecutionMessageLogDTO runMessage, AppRunTypeEnum runType);

    protected void consoleMessage(Long runId, ConsoleStdOutDTO runMessage, AppRunTypeEnum runType) {
        //ignoring console messages for now, can be implemented in the future if needed
    }


    protected void onOpen(String containerId, String connectionId) {
        Log.info("Connection opened: " + connectionId);
    }

    protected void onClose(String containerId, String connectionId) {
        Log.info("Connection closed: " + connectionId);
    }

    protected void onError(String containerId, Throwable throwable) {
        Log.error("Error in WebsocketClient: " + throwable.getMessage());
    }

    protected <T> void handleAppMessages(AppMessageWrapperDTO<T> m, Long runId, AppRunTypeEnum runType) {
        switch (m.getType()) {
            case START_RUN:
                StartRunDTO startRunDTO = decode(m.getMessage(), StartRunDTO.class);
                startRun(runId, startRunDTO, runType);
                break;
            case UPDATE_RUN:
                UpdateRunDTO updateTest = decode(m.getMessage(), UpdateRunDTO.class);
                updateRun(runId, updateTest, runType);
                break;
            case LOG_MESSAGE:
                ExecutionMessageLogDTO runMessage = decode(m.getMessage(), ExecutionMessageLogDTO.class);
                logMessage(runId, runMessage, runType);
                break;
            case CONSOLE_MESSAGE:
                ConsoleStdOutDTO consoleMessage = decode(m.getMessage(), ConsoleStdOutDTO.class);
                consoleMessage(runId, consoleMessage, runType);
                break;
            case FINISH_RUN:
                FinishRunDTO finishTest = decode(m.getMessage(), FinishRunDTO.class);
                finishRun(runId, finishTest, runType);
                break;
            default:
                Log.debugf("Not implemented message type: %s in the BaseAppServer", m.getType());
                break;
        }

    }

    protected <T> T decode(Object payload, Class<T> targetType) {
        return objectMapper.convertValue(payload, targetType);
    }
}
