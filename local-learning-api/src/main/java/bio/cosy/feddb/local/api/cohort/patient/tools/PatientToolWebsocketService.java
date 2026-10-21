package bio.cosy.feddb.local.api.cohort.patient.tools;

import bio.cosy.feddb.core.execution.message.ExecutionMessageLogDTO;
import bio.cosy.feddb.core.execution.message.ExecutionMessageMetricDTO;
import bio.cosy.feddb.core.execution.run.dto.AppMessageWrapperDTO;
import bio.cosy.feddb.core.execution.run.dto.FinishRunDTO;
import bio.cosy.feddb.core.execution.run.dto.StartRunDTO;
import bio.cosy.feddb.core.execution.run.dto.UpdateRunDTO;
import bio.cosy.feddb.core.execution.run.types.AppConnectionTypesEnum;
import bio.cosy.feddb.core.execution.run.types.AppMessageTypeEnum;
import bio.cosy.feddb.core.execution.run.types.AppRunTypeEnum;
import bio.cosy.feddb.core.execution.run.types.RunStatusTypes;
import bio.cosy.feddb.core.security.ToolAuthenticated;
import bio.cosy.feddb.core.security.ToolApiKeyAuthenticationMechanism;
import jakarta.annotation.security.RolesAllowed;
import bio.cosy.feddb.core.security.Scope;
import bio.cosy.feddb.core.security.ToolApiKeyService;
import bio.cosy.feddb.core.execution.server.WorkflowAppServer;
import bio.cosy.feddb.local.api.cohort.patient.tools.log.PatientToolRunLogBO;
import bio.cosy.feddb.local.services.orch.WorkflowOrchestratorBO;
import io.quarkus.logging.Log;
import io.quarkus.websockets.next.*;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;

@WebSocket(path = "/patient/tool/run/{runId}/{type}")
@ToolAuthenticated(Scope.PATIENT_TOOL_RUN)
@RolesAllowed(ToolApiKeyAuthenticationMechanism.ROLE)
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
        try {
            return setAppIsRunning(runId);
        } catch (Exception e) {
            workflowOrchestrator.cleanupStep(runId);
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
            workflowOrchestrator.cleanupStep(runId);
            //cleanup if the step is not found
            Log.error("Step not found for runId " + runId + ". Closing connection.");
            connection.closeAndAwait(RUN_NOT_FOUND);
        }
    }


    @Transactional
    public AppMessageWrapperDTO<StartRunDTO> setAppIsRunning(Long runId) {
        toolRunBO.persistStep(runId, RunStatusTypes.RUNNING);
        toolRunBO.appendStep(runId, "The app connected and received the patient data");

        StartRunDTO testDto = toolRunBO.getStartup(runId);
        return new AppMessageWrapperDTO<>(AppMessageTypeEnum.START_PREDICTION, testDto, AppRunTypeEnum.EXPERIMENT_RUN);
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
            if (AppConnectionTypesEnum.APP.isEqual(type)) {
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
    protected void logMetric(Long runId, ExecutionMessageMetricDTO runMetric, AppRunTypeEnum runType) {
        //ignored for patient tool runs
    }

    @Override
    @Transactional
    protected void logMessage(Long runId, ExecutionMessageLogDTO runMessage, AppRunTypeEnum runType) {
        stepMessageBO.create(runMessage, runId);
    }

}
