package bio.cosy.feddb.local.api.cohort.patient.tools;

import bio.cosy.feddb.core.api.run.*;
import bio.cosy.feddb.core.api.run.message.RunMessageLogDTO;
import bio.cosy.feddb.core.api.run.message.RunMessageMetricDTO;
import bio.cosy.feddb.core.security.ToolAuthenticated;
import bio.cosy.feddb.core.security.Scope;
import bio.cosy.feddb.core.security.ToolApiKeyService;
import bio.cosy.feddb.core.services.orch.WorkflowAppServer;
import bio.cosy.feddb.local.api.cohort.patient.tools.log.PatientToolRunLogBO;
import bio.cosy.feddb.local.services.orch.WorkflowOrchestratorBO;
import io.quarkus.logging.Log;
import io.quarkus.websockets.next.*;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;

@WebSocket(path = "/patient/tool/run/{runId}/{type}")
@ToolAuthenticated(Scope.PATIENT_TOOL_RUN)
public class PatientToolWebsocketService extends WorkflowAppServer {

    private static final CloseReason RUN_FINISHED =
            new CloseReason(1000, "Run finished successfully");

    private static final CloseReason RUN_NOT_FOUND =
            new CloseReason(1001, "Run not found");

    @Inject
    WebSocketConnection connection;

    @Inject
    WorkflowOrchestratorBO workflowOrchestrator;

    @Inject
    PatientToolRunBO toolRunBO;

    @Inject
    PatientToolRunLogBO stepMessageBO;

    @Inject
    ToolApiKeyService toolApiKeyService;

    @OnOpen
    public AppMessageWrapperDTO<StartRunDTO> onOpen(WebSocketConnection connection) {
        Long runId = Long.parseLong(connection.pathParam("runId"));
        Log.info("Learning-Connection opened: " + connection.id() + " for runId " + runId);
        String type = connection.pathParam("type");
        AppConnectionTypesEnum connectionType = AppConnectionTypesEnum.fromString(type);
        try {
            return setAppIsRunning(runId, connectionType);
        } catch (Exception e) {
            workflowOrchestrator.cleanupWorkflowNode(runId);
            //cleanup if the step is not found
            Log.error("Step not found for runId " + runId + ". Closing connection.");
            connection.closeAndAwait(RUN_NOT_FOUND);
        }
        return null;
    }

    @OnClose
    public void onClose(WebSocketConnection connection) {
        Long runId = Long.parseLong(connection.pathParam("runId"));
        toolApiKeyService.revoke(Scope.PATIENT_TOOL_RUN, runId);
        Log.info("Learning-Connection closed: " + connection.id() + " for runId " + runId);
    }

    @OnError
    public void onError(WebSocketConnection connection, Throwable throwable) {
        Log.error("Error in Learning-WebsocketClient: " + throwable.getMessage());
        Long runId = Long.parseLong(connection.pathParam("runId"));
        toolApiKeyService.revoke(Scope.PATIENT_TOOL_RUN, runId);
        try {
            setAppHasError(runId, throwable.getMessage());
        } catch (NotFoundException e) {
            workflowOrchestrator.cleanupWorkflowNode(runId);
            //cleanup if the step is not found
            Log.error("Step not found for runId " + runId + ". Closing connection.");
            connection.closeAndAwait(RUN_NOT_FOUND);
        }
    }


    @Transactional
    public AppMessageWrapperDTO<StartRunDTO> setAppIsRunning(Long runId, AppConnectionTypesEnum connectionType) {
        if (connectionType != AppConnectionTypesEnum.CONTROLLER) {
            toolRunBO.persistStep(runId, RunStatusTypes.RUNNING);
            toolRunBO.appendStep(runId, "The app connected and received the patient data");

            StartRunDTO testDto = toolRunBO.getStartup(runId);
            return new AppMessageWrapperDTO<>(AppMessageTypeEnum.START_PREDICTION, testDto, AppRunTypeEnum.EXPERIMENT_RUN);
        }
        throw new IllegalArgumentException("Only APP connections are supported for patient tool runs.");
    }

    @Transactional
    protected void setAppHasError(Long runId, String errorMessage) {
        PatientToolRunDTO step = toolRunBO.getById(runId);
        if (step.getRunStatus().equals(RunStatusTypes.ERROR) || step.getRunStatus().equals(RunStatusTypes.FINISHED)) {
            return;
        }
        if (step.getLastError() != null) {
            Log.info("Step " + runId + " already has an error: " + step.getLastError() + ". New error: " + errorMessage);
            return;
        }
        if (errorMessage.equals("Connection was closed")) {
            errorMessage = "The app closed the connection before reporting a result";
        }
        // Updates the entity itself: a DTO round trip would drop the run's cohort
        toolRunBO.fail(runId, errorMessage);
        toolRunBO.cleanupContainer(runId);
    }

    @OnTextMessage
    <T> void consume(AppMessageWrapperDTO<T> m) {
        String runIdString = connection.pathParam("runId");
        String type = connection.pathParam("type");
        try {
            Long runId = Long.parseLong(runIdString);
            Log.debug("Received message Text: " + m.getMessage() + " for container " + runId + " and type " + type + " of message type " + m.getType());
            if (AppConnectionTypesEnum.APP.isEqual(type) || AppConnectionTypesEnum.CONTROLLER.isEqual(type)) {
                handleAppMessages(m, runId, m.getRunType());
            }
        } catch (Exception e) {
            Log.error("Error handling message for app " + runIdString + " and type " + type, e);
        }

    }


    @Override
    @Transactional
    protected void startRun(Long runId, StartRunDTO startRun, AppRunTypeEnum runType) {
        toolRunBO.persistStep(runId, RunStatusTypes.STARTED);
    }

    @Override
    @Transactional
    protected void updateRun(Long runId, UpdateRunDTO updateTest, AppRunTypeEnum runType) {
        toolRunBO.persistStep(runId, updateTest);
        if (updateTest.getStatus().equals(RunStatusTypes.ERROR)) {
            toolRunBO.cleanupContainer(runId);
        }
    }

    @Override
    protected void finishRun(Long runId, FinishRunDTO finishTest, AppRunTypeEnum runType) {
        // Not transactional: collecting the outputs downloads them from the orchestrator
        toolRunBO.completeRun(runId);
        Log.info("Closing websocket connection for finished run " + runId);
        connection.closeAndAwait(RUN_FINISHED);
    }

    @Override
    @Transactional
    protected void logMetric(Long runId, RunMessageMetricDTO runMetric, AppRunTypeEnum runType) {
        //ignored for patient tool runs
    }

    @Override
    @Transactional
    protected void logMessage(Long runId, RunMessageLogDTO runMessage, AppRunTypeEnum runType) {
        stepMessageBO.create(runMessage, runId);
    }

}
