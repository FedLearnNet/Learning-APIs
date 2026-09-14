package bio.cosy.feddb.core.base;

import bio.cosy.feddb.core.api.run.*;
import bio.cosy.feddb.core.api.run.message.ConsoleStdOutDTO;
import bio.cosy.feddb.core.api.run.message.RunMessageLogDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.logging.Log;


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

    protected abstract void updateRun(Long runId, UpdateRunDTO updateTest, AppRunTypeEnum runType);

    protected abstract void finishRun(Long runId, FinishRunDTO finishTest, AppRunTypeEnum runType);

    protected abstract void startRun(Long runId, StartRunDTO startRun, AppRunTypeEnum runType);

    protected abstract void logMessage(Long runId, RunMessageLogDTO runMessage, AppRunTypeEnum runType);

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
        ObjectMapper objectMapper = new ObjectMapper();

        switch (m.getType()) {
            case START_RUN:
                StartRunDTO startRunDTO = objectMapper.convertValue(m.getMessage(), StartRunDTO.class);
                startRun(runId, startRunDTO, runType);
                break;
            case UPDATE_RUN:
                UpdateRunDTO updateTest = objectMapper.convertValue(m.getMessage(), UpdateRunDTO.class);
                updateRun(runId, updateTest, runType);
                break;
            case LOG_MESSAGE:
                RunMessageLogDTO runMessage = objectMapper.convertValue(m.getMessage(), RunMessageLogDTO.class);
                logMessage(runId, runMessage, runType);
                break;
            case CONSOLE_MESSAGE:
                ConsoleStdOutDTO consoleMessage = objectMapper.convertValue(m.getMessage(), ConsoleStdOutDTO.class);
                consoleMessage(runId, consoleMessage, runType);
                break;
            case FINISH_RUN:
                FinishRunDTO finishTest = objectMapper.convertValue(m.getMessage(), FinishRunDTO.class);
                finishRun(runId, finishTest, runType);
                break;
            default:
                Log.debugf("Not implemented message type: %s in the BaseAppServer", m.getType());
                break;
        }

    }
}
