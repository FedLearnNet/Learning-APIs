package de.unihamburg.daibetes.api.project.experiment.federated.aggregator;

import bio.cosy.feddb.core.api.run.*;
import bio.cosy.feddb.core.api.run.message.RunMessageLogDTO;
import bio.cosy.feddb.core.api.run.message.RunMessageMetricDTO;
import bio.cosy.feddb.core.security.Scope;
import bio.cosy.feddb.core.security.ToolApiKeyAuthenticationMechanism;
import bio.cosy.feddb.core.security.ToolApiKeyService;
import bio.cosy.feddb.core.security.ToolAuthenticated;
import bio.cosy.feddb.core.services.orch.WorkflowAppServer;
import io.quarkus.logging.Log;
import io.quarkus.websockets.next.*;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

// type = AppConnectionTypesEnum
@WebSocket(path = "/project/experiment/federated/flower-aggregator/{stepId}/{type}")
@ToolAuthenticated(Scope.FEDERATED_AGGREGATOR_RUN)
@RolesAllowed(ToolApiKeyAuthenticationMechanism.ROLE)
public class ProjectFederatedExperimentFlowerAggregatorWebsocketService extends WorkflowAppServer {

    @Inject
    WebSocketConnection connection;

    @Inject
    ProjectFederatedExperimentFlowerAggregatorBO flowerAggregatorBO;

    @Inject
    ToolApiKeyService toolApiKeyService;

    @OnOpen
    public AppMessageWrapperDTO<StartRunDTO> onOpen(WebSocketConnection connection) {
        Long stepId = Long.parseLong(connection.pathParam("stepId"));
        Log.info("Platform Flower aggregator connection opened: " + connection.id() + " for step " + stepId);
        StartRunDTO startDTO = flowerAggregatorBO.getStartup(stepId);
        return new AppMessageWrapperDTO<>(AppMessageTypeEnum.START_FEDERATED_RUN, startDTO, AppRunTypeEnum.FEDERATED_RUN);
    }

    @OnClose
    public void onClose(WebSocketConnection connection) {
        Long stepId = Long.parseLong(connection.pathParam("stepId"));
        toolApiKeyService.revoke(Scope.FEDERATED_AGGREGATOR_RUN, stepId);
        Log.info("Platform Flower aggregator connection closed: " + connection.id());
        flowerAggregatorBO.onConnectionClosed(stepId);
    }

    @OnError
    public void onError(WebSocketConnection connection, Throwable throwable) {
        toolApiKeyService.revoke(Scope.FEDERATED_AGGREGATOR_RUN,
                Long.parseLong(connection.pathParam("stepId")));
        Log.error("Error in platform Flower aggregator WebSocket: " + throwable.getMessage());
    }

    @OnTextMessage
    <T> void consume(AppMessageWrapperDTO<T> m) {
        String stepIdString = connection.pathParam("stepId");
        String type = connection.pathParam("type"); // has to be a AppConnectionTypesEnum
        try {
            Long stepId = Long.parseLong(stepIdString);
            Log.debug("Received message Text: " + m.getMessage() + " for platform Flower aggregator " + stepId + " and type " + type);
            if (AppConnectionTypesEnum.APP.isEqual(type)) {
                handleAppMessages(m, stepId, m.getRunType());
            } else {
                Log.error("Received message for unknown/not supported type: " + type);
            }
        } catch (Exception e) {
            Log.error("Error handling message for platform Flower aggregator " + stepIdString + " and type " + type, e);
        }
    }

    @Override
    @Transactional
    protected void updateRun(Long stepId, UpdateRunDTO updateTest, AppRunTypeEnum runType) {
        flowerAggregatorBO.onStatusUpdate(stepId, updateTest.getStatus(), updateTest.getError());
    }

    @Override
    @Transactional
    protected void finishRun(Long stepId, FinishRunDTO finishTest, AppRunTypeEnum runType) {
        // FINISH_FEDERATED_RUN also reports a failed run (status ERROR), so forward the actual status.
        RunStatusTypes status = finishTest.getStatus() != null ? finishTest.getStatus() : RunStatusTypes.FINISHED;
        flowerAggregatorBO.onStatusUpdate(stepId, status, finishTest.getError());
    }

    @Override
    @Transactional
    protected void startRun(Long stepId, StartRunDTO startRun, AppRunTypeEnum runType) {
        Log.infof("Platform Flower aggregator for step %d acknowledged start", stepId);
    }

    @Override
    @Transactional
    protected void logMessage(Long stepId, RunMessageLogDTO runMessage, AppRunTypeEnum runType) {
        Log.debugf("Platform Flower aggregator log for step %d: %s", stepId, runMessage);
    }

    @Override
    @Transactional
    protected void logMetric(Long stepId, RunMessageMetricDTO runMetric, AppRunTypeEnum runType) {
        Log.debugf("Platform Flower aggregator metric for step %d: %s", stepId, runMetric);
    }
}
