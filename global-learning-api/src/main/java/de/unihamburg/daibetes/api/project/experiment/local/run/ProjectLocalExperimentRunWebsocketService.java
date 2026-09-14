package de.unihamburg.daibetes.api.project.experiment.local.run;

import bio.cosy.feddb.core.api.run.*;
import bio.cosy.feddb.core.api.run.message.RunMessageLogDTO;
import bio.cosy.feddb.core.api.run.message.RunMessageMetricDTO;
import bio.cosy.feddb.core.security.ToolAuthenticated;
import bio.cosy.feddb.core.security.Scope;
import bio.cosy.feddb.core.security.ToolApiKeyService;
import bio.cosy.feddb.core.services.orch.WorkflowAppServer;
import de.unihamburg.daibetes.api.project.experiment.local.ProjectLocalExperimentBO;
import de.unihamburg.daibetes.api.project.experiment.local.message.ProjectLocalExperimentStepMessageBO;
import io.quarkus.logging.Log;
import io.quarkus.websockets.next.*;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

// type = AppConnectionTypesEnum
@WebSocket(path = "/project/experiment/local/{stepId}/{type}")
@ToolAuthenticated(Scope.LOCAL_EXPERIMENT_RUN)
public class ProjectLocalExperimentRunWebsocketService extends WorkflowAppServer {

    @Inject
    WebSocketConnection connection;


    @Inject
    ProjectLocalExperimentBO projectLocalExperimentBO;

    @Inject
    ProjectLocalExperimentStepMessageBO stepMessageBO;

    @Inject
    ToolApiKeyService toolApiKeyService;

    @OnOpen
    public AppMessageWrapperDTO<StartRunDTO> onOpen(WebSocketConnection connection) {
        Log.info("Connection opened: " + connection.id());
        Long stepId = Long.parseLong(connection.pathParam("stepId"));
        return startTest(stepId);
    }

    @OnClose
    public void onClose(WebSocketConnection connection) {
        toolApiKeyService.revoke(Scope.LOCAL_EXPERIMENT_RUN,
                Long.parseLong(connection.pathParam("stepId")));
        Log.info("Connection closed: " + connection.id());
    }

    @OnError
    public void onError(WebSocketConnection connection, Throwable throwable) {
        toolApiKeyService.revoke(Scope.LOCAL_EXPERIMENT_RUN,
                Long.parseLong(connection.pathParam("stepId")));
        Log.error("Error in WebsocketClient: " + throwable.getMessage());
    }


    @Transactional
    public AppMessageWrapperDTO<StartRunDTO> startTest(Long runId) {
        //TODO CALL MORE COMPLEX LOGIC
        StartRunDTO testDto = projectLocalExperimentBO.getStartup(runId);
        return new AppMessageWrapperDTO<>(AppMessageTypeEnum.START_PREDICTION, testDto, AppRunTypeEnum.EXPERIMENT_RUN);
    }


    @OnTextMessage
    <T> void consume(AppMessageWrapperDTO<T> m) {
        String runIdString = connection.pathParam("stepId");
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
        projectLocalExperimentBO.updateStatus(runId, updateTest);
    }

    @Override
    @Transactional
    protected void finishRun(Long runId, FinishRunDTO finishTest, AppRunTypeEnum runType) {
        projectLocalExperimentBO.updateStatus(runId, finishTest);
    }

    @Override
    @Transactional
    protected void startRun(Long runId, StartRunDTO startRun, AppRunTypeEnum runType) {
        projectLocalExperimentBO.updateStatus(runId, startRun);
    }

    @Override
    @Transactional
    protected void logMessage(Long runId, RunMessageLogDTO runMessage, AppRunTypeEnum runType) {
        stepMessageBO.logMessage(runId, runMessage);
    }

    @Override
    @Transactional
    protected void logMetric(Long runId, RunMessageMetricDTO runMetric, AppRunTypeEnum runType) {
        stepMessageBO.logMessage(runId, runMetric);
    }
}
