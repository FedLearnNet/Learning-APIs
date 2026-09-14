package bio.cosy.feddb.local.api.importer.run.execution;

import bio.cosy.feddb.core.api.model.ModelAppServer;
import bio.cosy.feddb.core.api.run.*;
import bio.cosy.feddb.core.api.run.message.ConsoleStdOutDTO;
import bio.cosy.feddb.core.api.run.message.RunMessageLogDTO;
import bio.cosy.feddb.core.security.ToolAuthenticated;
import bio.cosy.feddb.core.security.Scope;
import bio.cosy.feddb.core.security.ToolApiKeyService;
import bio.cosy.feddb.local.api.importer.run.message.ConnectorRunRunMessagesBO;
import bio.cosy.feddb.local.api.importer.run.step.ConnectorRunStepBO;
import bio.cosy.feddb.local.api.importer.run.step.ConnectorRunStepDTO;
import io.quarkus.logging.Log;
import io.quarkus.websockets.next.*;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;

import java.util.LinkedHashMap;

// type = AppConnectionTypesEnum
@WebSocket(path = "/connectors/run/execution/{runId}/{type}")
@ToolAuthenticated(Scope.CONNECTOR_RUN)
public class ConnectorRunExecutionWebsocketService extends ModelAppServer {

    private static final CloseReason RUN_FINISHED =
            new CloseReason(1000, "Run finished successfully");

    private static final CloseReason RUN_NOT_FOUND =
            new CloseReason(1001, "Run not found");

    @Inject
    WebSocketConnection connection;

    @Inject
    ConnectorRunRunMessagesBO stepMessageBO;

    @Inject
    ConnectorRunStepBO stepBO;

    @Inject
    bio.cosy.feddb.local.api.importer.transformer.AppTransformerSessionBO sessionBO;

    @Inject
    ToolApiKeyService toolApiKeyService;

    @OnOpen
    public void onOpen(WebSocketConnection connection) {
        Log.info("Connection opened: " + connection.id());
        Long runId = Long.parseLong(connection.pathParam("runId"));
        AppMessageWrapperDTO<StartRunDTO> start = startTest(runId);
        if (start == null || start.getType() == null) {
            // Send nothing at all. A returned value is serialized straight to the app, so an
            // envelope with no type reaches it as {"type":null,...}, which it cannot dispatch - it
            // closed the connection on the spot. There is genuinely nothing to say here when the
            // step is not startable or when an app-transformer session drives its own runs.
            return;
        }
        connection.sendTextAndAwait(start);
    }

    // Returns void rather than a Uni on purpose: a reactive return type makes websockets-next run
    // this on the event loop, where the @Transactional lookup inside startTest cannot open a JTA
    // transaction ("@Transactional cannot start a JTA transaction within a reactive pipeline"),
    // which failed the connection the moment the app opened it.

    @OnClose
    public void onClose(WebSocketConnection connection) {
        Log.info("Connection closed: " + connection.id() + " Reason: " + connection.closeReason());
        Long runId = Long.parseLong(connection.pathParam("runId"));
        revokeUnlessSessionDriven(runId);
        if (RUN_FINISHED.equals(connection.closeReason())) {
            return;
        }
        closedRun(runId);
    }

    @OnError
    public void onError(WebSocketConnection connection, Throwable throwable) {
        Log.error("Error in WebsocketClient: " + throwable.getMessage());
        Long runId = Long.parseLong(connection.pathParam("runId"));
        revokeUnlessSessionDriven(runId);
        closedRun(runId);
    }


    @Transactional
    public AppMessageWrapperDTO<StartRunDTO> startTest(Long runId) {
        Log.info("Starting test for runId: " + runId);
        //TODO
        if (sessionBO.isSessionStep(runId)) {
            // A batched transformer drives its own runs: the app has exactly one worker slot, and
            // starting an empty run here would consume it and make every batch bounce off
            // "App is already running".
            Log.infof("Step %d is driven by an app transformer session, not starting it on connect", runId);
            return null;
        }

        ConnectorRunStepDTO prediction = stepBO.getById(runId);
        if (prediction == null) {
            throw new NotFoundException("Run with id " + runId + " not found");
        }
        if (!RunStatusTypes.canBeStartedStatus(prediction.getStatus())) {
            return null;
        }

        LinkedHashMap<String, String> inputFiles = prediction.getInputPaths();
        if (inputFiles == null) {
            inputFiles = new LinkedHashMap<>();
        }

        StartRunDTO testDto = new StartRunDTO();
        testDto.setId(runId);
        testDto.setInputData(new LinkedHashMap<>());
        testDto.setInputFilePaths(inputFiles);
        testDto.setStatus(RunStatusTypes.PENDING);
        testDto.setHyperParams(prediction.getHyperParams());
        return new AppMessageWrapperDTO<>(AppMessageTypeEnum.START_PREDICTION, testDto, AppRunTypeEnum.MODEL_RUN);

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
        stepBO.updateAndNotify(runId, updateTest.getStatus(), updateTest.getError());
    }

    @Override
    protected void finishRun(Long runId, FinishRunDTO finishTest, AppRunTypeEnum runType) {
        Log.infof("App run %d reported websocket completion. Waiting for uploadOutput() before finishing the step.",
                runId);

        if (sessionBO.isSessionStep(runId)) {
            // One batch finished, not the step. The container serves the whole import, so closing
            // its connection here would end the session after the very first batch.
            Log.infof("Batch of app transformer step %d finished; keeping the connection for the next batch", runId);
            return;
        }

        Log.info("Closing websocket connection for finished run " + runId);
        closeRun();

    }

    /**
     * Retires a step's credential, unless a batched transformer session still needs it.
     *
     * <p>A step's API key normally dies with its connection, because the connection is the run. A
     * session-driven step outlives many connections: the app reconnects on its own timer, and
     * revoking here meant every reconnect was answered with 401 until the run gave up. The session
     * revokes the key when it stops the container.</p>
     */
    private void revokeUnlessSessionDriven(Long runId) {
        if (sessionBO.isSessionStep(runId)) {
            Log.debugf("Keeping the API key of app transformer step %d for its next connection", runId);
            return;
        }
        toolApiKeyService.revoke(Scope.CONNECTOR_RUN, runId);
    }

    public void closedRun(Long runId) {
        try {
            if (sessionBO.isSessionStep(runId)) {
                // The wrapper's socket client reconnects on its own every few seconds
                // (_manage_connection), so a closed connection is not the end of a batched
                // transformer - failing the step here would also tear its container down and end
                // the import on the first blip. The session owns this step's lifecycle.
                Log.infof("App transformer step %d disconnected; its session will wait for it to reconnect", runId);
                return;
            }
            stepBO.failIfUnfinished(runId, "The app closed its connection before reporting a result");
            Log.info("Closing websocket connection for finished run " + runId);
            closeRun();
        } catch (Exception e) {
            Log.errorf("Error while closing run %d : %s", runId, e.getMessage());
        }
    }

    @Override
    protected void startRun(Long runId, StartRunDTO startRun, AppRunTypeEnum runType) {
        stepBO.updateAndNotify(runId, RunStatusTypes.STARTED, null);
    }

    @Override
    @Transactional
    protected void logMessage(Long runId, RunMessageLogDTO runMessage, AppRunTypeEnum runType) {
        stepMessageBO.create(runMessage, runId);
    }

    @Override
    // @Transactional
    protected void consoleMessage(Long runId, ConsoleStdOutDTO runMessage, AppRunTypeEnum runType) {
        //ingore
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
