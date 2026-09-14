package bio.cosy.feddb.local.api.learning.project.run;

import bio.cosy.feddb.core.api.run.*;
import bio.cosy.feddb.core.api.run.message.RunMessageLogDTO;
import bio.cosy.feddb.core.api.run.message.RunMessageMetricDTO;
import bio.cosy.feddb.core.security.ToolAuthenticated;
import bio.cosy.feddb.core.security.Scope;
import bio.cosy.feddb.core.security.ToolApiKeyService;
import bio.cosy.feddb.core.services.orch.WorkflowAppServer;
import bio.cosy.feddb.local.api.learning.project.run.data.FederatedLearningExperimentStepDataBO;
import bio.cosy.feddb.local.api.learning.project.run.message.FederatedLearningExperimentStepMessageBO;
import bio.cosy.feddb.local.api.learning.project.run.step.FederatedLearningExperimentStepBO;
import bio.cosy.feddb.local.api.learning.project.run.step.FederatedLearningExperimentStepDTO;
import bio.cosy.feddb.local.services.orch.WorkflowOrchestratorBO;
import io.quarkus.logging.Log;
import io.quarkus.websockets.next.*;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;

@WebSocket(path = "/learning/run/{runId}/{type}")
@ToolAuthenticated(Scope.LEARNING_RUN)
public class FederatedLearningExperimentWebsocketService extends WorkflowAppServer {

    private static final CloseReason RUN_FINISHED =
            new CloseReason(1000, "Run finished successfully");

    private static final CloseReason RUN_FAILED =
            new CloseReason(1000, "Run finished with error");

    private static final CloseReason RUN_NOT_FOUND =
            new CloseReason(1001, "Run not found");

    @Inject
    WebSocketConnection connection;

    @Inject
    FederatedLearningExperimentStepBO stepBO;

    @Inject
    WorkflowOrchestratorBO workflowOrchestrator;

    @Inject
    FederatedLearningExperimentStepMessageBO stepMessageBO;

    @Inject
    FederatedLearningExperimentStepDataBO stepResultBO;

    @Inject
    ToolApiKeyService toolApiKeyService;

    @OnOpen
    public void onOpen(WebSocketConnection connection) {
        Long runId = Long.parseLong(connection.pathParam("runId"));
        Log.info("Learning-Connection opened: " + connection.id() + " for runId " + runId);
        String type = connection.pathParam("type");
        AppConnectionTypesEnum connectionType = AppConnectionTypesEnum.fromString(type);
        try {
            setAppIsRunning(runId, connectionType);
        } catch (NotFoundException e) {
            workflowOrchestrator.cleanupWorkflowNode(runId);
            //cleanup if the step is not found
            Log.error("Step not found for runId " + runId + ". Closing connection.");
            connection.closeAndAwait(RUN_NOT_FOUND);
        }
    }

    @OnClose
    public void onClose(WebSocketConnection connection) {
        Long runId = Long.parseLong(connection.pathParam("runId"));
        toolApiKeyService.revoke(Scope.LEARNING_RUN, runId);
        Log.info("Learning-Connection closed: " + connection.id() + " for runId " + runId);
    }

    @OnError
    public void onError(WebSocketConnection connection, Throwable throwable) {
        Log.error("Error in Learning-WebsocketClient: " + throwable.getMessage());
        Long runId = Long.parseLong(connection.pathParam("runId"));
        toolApiKeyService.revoke(Scope.LEARNING_RUN, runId);
        try {
            setAppHasError(runId, throwable.getMessage());
        } catch (NotFoundException e) {
            workflowOrchestrator.cleanupWorkflowNode(runId);
            //cleanup if the step is not found
            Log.error("Step not found for runId " + runId + ". Closing connection.");
            connection.closeAndAwait(RUN_NOT_FOUND);
        }
    }


    public void setAppIsRunning(Long runId, AppConnectionTypesEnum connectionType) {
        FederatedLearningExperimentStepDTO step = stepBO.getById(runId);
        if (connectionType == AppConnectionTypesEnum.CONTROLLER) {
            step.setStepStatus(RunStatusTypes.RUNNING);
        } else {
            step.setStepStatus(RunStatusTypes.STARTED);
        }
        stepBO.updateStatus(step);
    }

    protected void setAppHasError(Long runId, String errorMessage) {
        FederatedLearningExperimentStepDTO step = stepBO.getById(runId);
        if (step.getStepStatus().equals(RunStatusTypes.ERROR) || step.getStepStatus().equals(RunStatusTypes.FINISHED)) {
            return;
        }
        if (step.getLastError() != null) {
            Log.info("Step " + runId + " already has an error: " + step.getLastError() + ". New error: " + errorMessage);
            return;
        }
        // An abnormal websocket close is NOT a successful finish. Genuine completion is signalled via
        // FINISH_FEDERATED_RUN -> finishRun(), which sets FINISHED and closes with code 1000 (-> @OnClose).
        // Reaching @OnError while the step is still non-terminal means the app/container died unexpectedly
        // (e.g. the run failed to start and was torn down), so we must record it as an error and let it
        // propagate to global (stop the learning) instead of masking it as FINISHED.
        step.setStepStatus(RunStatusTypes.ERROR);
        step.setLastError(errorMessage);
        stepBO.updateStatus(step);
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
    protected void startRun(Long runId, StartRunDTO startRun, AppRunTypeEnum runType) {
        FederatedLearningExperimentStepDTO step = stepBO.getById(runId);
        step.setStepStatus(RunStatusTypes.STARTED);
        stepBO.updateStatus(step);
    }

    @Override
    protected void updateRun(Long runId, UpdateRunDTO updateTest, AppRunTypeEnum runType) {
        FederatedLearningExperimentStepDTO step = stepBO.getById(runId);
        step.setStepStatus(updateTest.getStatus());
        step.setProgress(updateTest.getProgress());
        if (updateTest.getStatus().equals(RunStatusTypes.ERROR)) {
            step.setLastError(updateTest.getError());
        }
        stepBO.updateStatus(step);
    }

    @Override
    protected void finishRun(Long runId, FinishRunDTO finishTest, AppRunTypeEnum runType) {
        FederatedLearningExperimentStepDTO step = stepBO.getById(runId);
        Log.infof("Run completion received: runId=%d runType=%s connectionId=%s reportedStatus=%s reportedError=%s persistedStatus=%s",
                runId, runType, connection.id(), finishTest.getStatus(), finishTest.getError(), step.getStepStatus());
        // A failed federated run can report its error only in the finish message.
        // Keep an earlier error when present, otherwise persist the supplied failure.
        boolean isError = finishTest.getStatus() == RunStatusTypes.ERROR || finishTest.getError() != null
                || step.getStepStatus() == RunStatusTypes.ERROR || step.getLastError() != null;
        if (isError) {
            String error = step.getLastError() != null ? step.getLastError()
                    : finishTest.getError() != null && !finishTest.getError().isBlank()
                    ? finishTest.getError() : "Run finished with error";
            // Persist as ERROR with the message so stepBO.updateStatus reports it to global (marks the
            // experiment errored and stops learning) instead of silently recording a successful finish.
            Log.errorf("Run %d finished with error reported by app: %s", runId, error);
            step.setStepStatus(RunStatusTypes.ERROR);
            step.setLastError(error);
        } else {
            // Collect the step's output files from the orch volume BEFORE updateStatus: on the last
            // step its final-state handling deletes the containers and volumes, so saving afterwards
            // would read an already-removed volume and lose the results.
            try {
                stepResultBO.saveResults(runId);
                step.setStepStatus(RunStatusTypes.FINISHED);
            } catch (Exception e) {
                Log.errorf(e, "Failed to save results for run %d; marking run as failed", runId);
                step.setStepStatus(RunStatusTypes.ERROR);
                step.setLastError("Failed to save results: " + e.getMessage());
                isError = true;
            }
        }
        stepBO.updateStatus(step);
        Log.info("Closing websocket connection for run " + runId + (isError ? " (error)" : " (finished)"));
        connection.closeAndAwait(isError ? RUN_FAILED : RUN_FINISHED);
    }

    @Override
    @Transactional
    protected void logMetric(Long runId, RunMessageMetricDTO runMetric, AppRunTypeEnum runType) {
        stepMessageBO.create(runMetric, runId);
    }

    @Override
    @Transactional
    protected void logMessage(Long runId, RunMessageLogDTO runMessage, AppRunTypeEnum runType) {
        stepMessageBO.create(runMessage, runId);
    }

}
