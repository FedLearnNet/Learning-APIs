package de.unihamburg.daibetes.api.project.experiment.federated.aggregator;

import bio.cosy.feddb.core.api.run.*;
import bio.cosy.feddb.core.api.run.message.RunMessageLogDTO;
import bio.cosy.feddb.core.api.run.message.RunMessageMetricDTO;
import bio.cosy.feddb.core.security.Scope;
import bio.cosy.feddb.core.security.ToolApiKeyService;
import bio.cosy.feddb.core.security.ToolAuthenticated;
import bio.cosy.feddb.core.services.orch.WorkflowAppServer;
import io.quarkus.logging.Log;
import io.quarkus.websockets.next.*;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

// type = AppConnectionTypesEnum (APP for the container connecting directly, CONTROLLER if the
// platform's own Controller sidecar proxies the connection on the container's behalf)
@WebSocket(path = "/project/experiment/federated/aggregator/{stepId}/{type}")
@ToolAuthenticated(Scope.FEDERATED_AGGREGATOR_RUN)
public class ProjectFederatedExperimentAggregatorWebsocketService extends WorkflowAppServer {

    @Inject
    WebSocketConnection connection;

    @Inject
    ProjectFederatedExperimentAggregatorBO aggregatorBO;

    @Inject
    ToolApiKeyService toolApiKeyService;

    @OnOpen
    public AppMessageWrapperDTO<StartRunDTO> onOpen(WebSocketConnection connection) {
        Long stepId = Long.parseLong(connection.pathParam("stepId"));
        Log.info("Platform aggregator connection opened: " + connection.id() + " for step " + stepId);
        StartRunDTO startDTO = aggregatorBO.getStartup(stepId);
        return new AppMessageWrapperDTO<>(AppMessageTypeEnum.START_PREDICTION, startDTO, AppRunTypeEnum.FEDERATED_RUN);
    }

    @OnClose
    public void onClose(WebSocketConnection connection) {
        toolApiKeyService.revoke(Scope.FEDERATED_AGGREGATOR_RUN,
                Long.parseLong(connection.pathParam("stepId")));
        Log.info("Platform aggregator connection closed: " + connection.id());
    }

    @OnError
    public void onError(WebSocketConnection connection, Throwable throwable) {
        toolApiKeyService.revoke(Scope.FEDERATED_AGGREGATOR_RUN,
                Long.parseLong(connection.pathParam("stepId")));
        Log.error("Error in platform aggregator WebSocket: " + throwable.getMessage());
    }

    @OnTextMessage
    <T> void consume(AppMessageWrapperDTO<T> m) {
        String stepIdString = connection.pathParam("stepId");
        String type = connection.pathParam("type"); // has to be a AppConnectionTypesEnum
        try {
            Long stepId = Long.parseLong(stepIdString);
            Log.debug("Received message Text: " + m.getMessage() + " for platform aggregator " + stepId + " and type " + type);
            if (AppConnectionTypesEnum.APP.isEqual(type) || AppConnectionTypesEnum.CONTROLLER.isEqual(type)) {
                handleAppMessages(m, stepId, m.getRunType());
            } else {
                Log.error("Received message for unknown/not supported type: " + type);
            }
        } catch (Exception e) {
            Log.error("Error handling message for platform aggregator " + stepIdString + " and type " + type, e);
        }
    }

    @Override
    @Transactional
    protected void updateRun(Long stepId, UpdateRunDTO updateTest, AppRunTypeEnum runType) {
        aggregatorBO.onStatusUpdate(stepId, updateTest.getStatus(), updateTest.getError());
    }

    @Override
    @Transactional
    protected void finishRun(Long stepId, FinishRunDTO finishTest, AppRunTypeEnum runType) {
        aggregatorBO.onStatusUpdate(stepId, RunStatusTypes.FINISHED, null);
    }

    @Override
    @Transactional
    protected void startRun(Long stepId, StartRunDTO startRun, AppRunTypeEnum runType) {
        Log.infof("Platform aggregator for step %d acknowledged start", stepId);
    }

    @Override
    @Transactional
    protected void logMessage(Long stepId, RunMessageLogDTO runMessage, AppRunTypeEnum runType) {
        Log.debugf("Platform aggregator log for step %d: %s", stepId, runMessage);
    }

    @Override
    @Transactional
    protected void logMetric(Long stepId, RunMessageMetricDTO runMetric, AppRunTypeEnum runType) {
        Log.debugf("Platform aggregator metric for step %d: %s", stepId, runMetric);
    }
}
