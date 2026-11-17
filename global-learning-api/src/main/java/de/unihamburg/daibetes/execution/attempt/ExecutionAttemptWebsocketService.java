package de.unihamburg.daibetes.execution.attempt;

import bio.cosy.feddb.core.execution.attempt.BaseAttemptLifecycle;
import bio.cosy.feddb.core.execution.run.dto.AppMessageWrapperDTO;
import bio.cosy.feddb.core.execution.run.dto.StartRunDTO;
import bio.cosy.feddb.core.execution.run.types.AppMessageTypeEnum;
import bio.cosy.feddb.core.execution.run.types.AppRunTypeEnum;
import bio.cosy.feddb.core.execution.server.BaseAttemptAppServer;
import bio.cosy.feddb.core.security.Scope;
import bio.cosy.feddb.core.security.ToolApiKeyAuthenticationMechanism;
import bio.cosy.feddb.core.security.ToolAuthenticated;
import io.quarkus.websockets.next.*;
import io.smallrye.common.annotation.Blocking;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import de.unihamburg.daibetes.api.development.run.federated.HostedFederatedTestBO;
import de.unihamburg.daibetes.api.development.run.federated.FederatedParticipantUpdateDTO;
import de.unihamburg.daibetes.api.development.run.federated.FederatedRoundMessageCreateDTO;
import bio.cosy.feddb.core.execution.message.ExecutionMessageLogDTO;
import bio.cosy.feddb.core.execution.message.ExecutionMessageMetricDTO;

// type = AppConnectionTypesEnum; runId is the attempt the app runs: an analysis or a training on the platform.
@WebSocket(path = "/execution/{mode}/{runId}/{type}")
@ToolAuthenticated({Scope.EXECUTION_ATTEMPT, Scope.MODEL_PREDICTION_RUN, Scope.MODEL_WORKFLOW_RUN})
@RolesAllowed(ToolApiKeyAuthenticationMechanism.ROLE)
public class ExecutionAttemptWebsocketService extends BaseAttemptAppServer {

    @Inject
    WebSocketConnection connection;

    @Inject
    ExecutionAttemptLifecycle lifecycle;

    @Inject HostedFederatedTestBO hostedTests;

    @OnOpen
    @Blocking
    public Uni<Void> onOpen(WebSocketConnection connection) {
        return sendStart(connection);
    }

    // The key is revoked once the attempt ends. An app gone before it finished failed.
    @OnClose
    public void onClose(WebSocketConnection connection) {
        failUnfinishedAttempt(connection);
    }

    @OnError
    public void onError(WebSocketConnection connection, Throwable throwable) {
        failUnfinishedAttempt(connection);
    }

    @OnTextMessage
    @Blocking
    <T> void onMessage(AppMessageWrapperDTO<T> message) {
        consume(connection, message);
    }

    @Override
    protected AppMessageWrapperDTO<StartRunDTO> startMessageOf(StartRunDTO start) {
        // Only an app joining a relay gets its config, e.g. the platform's aggregator of a training across clinics.
        if (start.getConfig() != null) {
            return new AppMessageWrapperDTO<>(AppMessageTypeEnum.START_FEDERATED_RUN, start,
                    start.isDistributedTest() ? AppRunTypeEnum.FEDERATED_TEST_RUN : AppRunTypeEnum.FEDERATED_RUN);
        }
        AppMessageTypeEnum type = lifecycle.predictsTransactional(start.getId())
                ? AppMessageTypeEnum.START_PREDICTION
                : AppMessageTypeEnum.START_RUN;
        return new AppMessageWrapperDTO<>(type, start,
                lifecycle.isTestTransactional(start.getId()) ? AppRunTypeEnum.TEST_RUN : AppRunTypeEnum.MODEL_RUN);
    }

    @Override
    protected BaseAttemptLifecycle<?, ?, ?, ?> lifecycle() {
        return lifecycle;
    }

    @Override
    protected <T> void handleAppMessages(AppMessageWrapperDTO<T> message, Long attemptId, AppRunTypeEnum runType) {
        if (runType == AppRunTypeEnum.FEDERATED_TEST_RUN && message.getType() == AppMessageTypeEnum.FEDERATED_PARTICIPANT_UPDATE) {
            hostedTests.updateParticipantTransactional(attemptId, decode(message.getMessage(), FederatedParticipantUpdateDTO.class));
        } else if (runType == AppRunTypeEnum.FEDERATED_TEST_RUN && message.getType() == AppMessageTypeEnum.FEDERATED_ROUND_MESSAGE) {
            hostedTests.storeRoundTransactional(attemptId, decode(message.getMessage(), FederatedRoundMessageCreateDTO.class));
        } else if ((runType == AppRunTypeEnum.FEDERATED_TEST_RUN || runType == AppRunTypeEnum.TEST_RUN)
                && message.getType() == AppMessageTypeEnum.LOG_METRIC) {
            ExecutionMessageMetricDTO metric = decode(message.getMessage(), ExecutionMessageMetricDTO.class);
            lifecycle.storeMetricTransactional(attemptId, metric);
            hostedTests.publishTransactional(attemptId, AppMessageTypeEnum.LOG_METRIC, metric);
        } else {
            super.handleAppMessages(message, attemptId, runType);
        }
    }

    @Override
    protected void logMessage(Long attemptId, ExecutionMessageLogDTO log, AppRunTypeEnum runType) {
        super.logMessage(attemptId, log, runType);
        if (runType == AppRunTypeEnum.FEDERATED_TEST_RUN || runType == AppRunTypeEnum.TEST_RUN)
            hostedTests.publishTransactional(attemptId, AppMessageTypeEnum.LOG_MESSAGE, log);
    }

    @Override
    protected WebSocketConnection connection() {
        return connection;
    }
}
