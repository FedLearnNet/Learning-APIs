package de.unihamburg.daibetes.api.analysis.run;

import bio.cosy.feddb.core.api.model.ModelAppServer;
import bio.cosy.feddb.core.api.model.prediction.DataAnalysisPredictionDTO;
import bio.cosy.feddb.core.api.model.prediction.DataAnalysisRunModesEnum;
import bio.cosy.feddb.core.api.model.workflow.DataAnalysisFileDTO;
import bio.cosy.feddb.core.api.run.*;
import bio.cosy.feddb.core.api.run.message.ConsoleStdOutDTO;
import bio.cosy.feddb.core.api.run.message.RunMessageLogDTO;
import bio.cosy.feddb.core.security.ToolAuthenticated;
import bio.cosy.feddb.core.security.Scope;
import bio.cosy.feddb.core.security.ToolApiKeyService;
import de.unihamburg.daibetes.api.analysis.prediction.DataAnalysisPredictionBO;
import de.unihamburg.daibetes.api.analysis.worklfow.DataAnalysisWorkflowRunBO;
import de.unihamburg.daibetes.api.analysis.worklfow.message.DataAnalysisWorkflowRunMessagesBO;
import de.unihamburg.daibetes.api.analysis.worklfow.step.DataAnalysisWorkflowRunStepBO;
import de.unihamburg.daibetes.api.analysis.worklfow.step.DataAnalysisWorkflowRunStepDTO;
import io.quarkus.logging.Log;
import io.quarkus.websockets.next.*;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;

import java.util.LinkedHashMap;
import java.util.List;

// type = AppConnectionTypesEnum
@WebSocket(path = "/model/run/{mode}/{runId}/{type}")
@ToolAuthenticated({Scope.MODEL_PREDICTION_RUN, Scope.MODEL_WORKFLOW_RUN})
public class DataAnalysisRunWebsocketService extends ModelAppServer {

    private static final CloseReason RUN_FINISHED =
            new CloseReason(1000, "Run finished successfully");

    private static final CloseReason RUN_NOT_FOUND =
            new CloseReason(1001, "Run not found");

    @Inject
    WebSocketConnection connection;

    @Inject
    DataAnalysisPredictionBO modelPredictionBO;

    @Inject
    DataAnalysisWorkflowRunMessagesBO stepMessageBO;

    @Inject
    DataAnalysisWorkflowRunStepBO stepBO;

    @Inject
    DataAnalysisWorkflowRunBO workflowRunBO;

    @Inject
    ToolApiKeyService toolApiKeyService;

    @OnOpen
    public AppMessageWrapperDTO<StartRunDTO> onOpen(WebSocketConnection connection) {
        Log.info("Connection opened: " + connection.id());
        Long runId = Long.parseLong(connection.pathParam("runId"));
        return startTest(runId);
    }

    @OnClose
    public void onClose(WebSocketConnection connection) {
        Log.info("Connection closed: " + connection.id() + " Reason: " + connection.closeReason());
        Long runId = Long.parseLong(connection.pathParam("runId"));
        toolApiKeyService.revoke(getToolApiKeyScope(), runId);
        closedRun(runId);
    }

    @OnError
    public void onError(WebSocketConnection connection, Throwable throwable) {
        Log.error("Error in WebsocketClient: " + throwable.getMessage());
        Long runId = Long.parseLong(connection.pathParam("runId"));
        toolApiKeyService.revoke(getToolApiKeyScope(), runId);
        closedRun(runId);
    }


    @Transactional
    public AppMessageWrapperDTO<StartRunDTO> startTest(Long runId) {
        Log.info("Starting test for runId: " + runId);
        if (isWorkflowRun()) {
            DataAnalysisWorkflowRunStepDTO step = stepBO.getById(runId);
            if (step == null) {
                throw new NotFoundException("WF Run step with id " + runId + " not found");
            }
            if (!RunStatusTypes.canBeStartedStatus(step.getStepStatus())) {
                return new AppMessageWrapperDTO<>();
            }
            StartRunDTO testDto = workflowRunBO.getStartup(runId);
            return new AppMessageWrapperDTO<>(AppMessageTypeEnum.START_PREDICTION, testDto, AppRunTypeEnum.MODEL_RUN);
        } else {
            DataAnalysisPredictionDTO prediction = modelPredictionBO.getById(runId);
            if (prediction == null) {
                throw new NotFoundException("Run with id " + runId + " not found");
            }
            if (!RunStatusTypes.canBeStartedStatus(prediction.getStatus())) {
                return new AppMessageWrapperDTO<>();
            }
            List<DataAnalysisFileDTO> inputFiles = prediction.getInputFiles();
            inputFiles.forEach(file -> {
                String filePath = file.getFile().getDownloadContentUrlSecret();
                prediction.getInputs().put(file.getInputName(), filePath);
            });

            StartRunDTO testDto = new StartRunDTO();
            testDto.setId(runId);
            testDto.setInputData(prediction.getInputs());
            testDto.setInputFilePaths(new LinkedHashMap<>());
            testDto.setStatus(RunStatusTypes.PENDING);
            testDto.setHyperParams(prediction.getHyperParams());
            return new AppMessageWrapperDTO<>(AppMessageTypeEnum.START_PREDICTION, testDto, AppRunTypeEnum.MODEL_RUN);
        }
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
    @Transactional
    protected void updateRun(Long runId, UpdateRunDTO updateTest, AppRunTypeEnum runType) {
        if (isWorkflowRun()) {
            workflowRunBO.updateAndNotify(runId, updateTest);
            //cleanup is handled in the stepBO
        } else {
            modelPredictionBO.updateAndNotify(runId, updateTest.getStatus(), updateTest.getError());

        }
    }

    @Override
    @Transactional
    protected void finishRun(Long runId, FinishRunDTO finishTest, AppRunTypeEnum runType) {
        if (isWorkflowRun()) {
            workflowRunBO.updateAndNotify(runId, finishTest);
        } else {
            // Persist the wrapper-measured timing first so the notified result carries runtime.
            modelPredictionBO.persistTiming(runId, finishTest);
            modelPredictionBO.updateAndNotify(runId, RunStatusTypes.FINISHED, null);
        }
        Log.info("Closing websocket connection for finished run " + runId);
        closeRun();

    }

    @Transactional
    public void closedRun(Long runId) {
        try {
            if (isWorkflowRun()) {
                workflowRunBO.stopRunByStep(runId);
            } else {
                modelPredictionBO.updateAndNotify(runId, RunStatusTypes.ERROR, null);
            }
            Log.info("Closing websocket connection for finished run " + runId);
            closeRun();
        } catch (Exception e) {
            Log.errorf("Error while closing run %d : %s", runId, e.getMessage());
        }
    }

    @Override
    @Transactional
    protected void startRun(Long runId, StartRunDTO startRun, AppRunTypeEnum runType) {
        if (isWorkflowRun()) {
            workflowRunBO.updateAndNotify(runId, startRun);
        } else {
            modelPredictionBO.updateAndNotify(runId, RunStatusTypes.STARTED, null);
        }
    }

    @Override
    @Transactional
    protected void logMessage(Long runId, RunMessageLogDTO runMessage, AppRunTypeEnum runType) {
        if (!isWorkflowRun()) {
            modelPredictionBO.updateAndNotify(runId, null, runMessage.getMessage());
        }
        stepMessageBO.create(runMessage, getClientMode(), runId);
    }

    @Override
    @Transactional
    protected void consoleMessage(Long runId, ConsoleStdOutDTO runMessage, AppRunTypeEnum runType) {
        if (!isWorkflowRun()) {
            modelPredictionBO.updateConsoleMsg(runId, runMessage.getMsg());
        }
        //stepMessageBO.create(runMessage, getClientMode(), runId);
    }


    private boolean isWorkflowRun() {
        return DataAnalysisRunModesEnum.WORKFLOW.equals(getClientMode());
    }

    private DataAnalysisRunModesEnum getClientMode() {
        String modeString = connection.pathParam("mode");
        return DataAnalysisRunModesEnum.fromString(modeString);
    }

    private Scope getToolApiKeyScope() {
        return isWorkflowRun() ? Scope.MODEL_WORKFLOW_RUN : Scope.MODEL_PREDICTION_RUN;
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
