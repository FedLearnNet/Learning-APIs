package de.unihamburg.daibetes.api.development.testembed;

import bio.cosy.feddb.core.api.app.FederatedAppDetailDTO;
import bio.cosy.feddb.core.execution.message.ExecutionMessageLogDTO;
import bio.cosy.feddb.core.execution.message.ExecutionMessageMetricDTO;
import bio.cosy.feddb.core.execution.run.dto.AppMessageWrapperDTO;
import bio.cosy.feddb.core.execution.run.dto.FederatedRunLifecycleDTO;
import bio.cosy.feddb.core.execution.run.dto.FinishRunDTO;
import bio.cosy.feddb.core.execution.run.dto.UpdateRunDTO;
import bio.cosy.feddb.core.execution.run.types.AppConnectionTypesEnum;
import bio.cosy.feddb.core.execution.run.types.AppMessageTypeEnum;
import bio.cosy.feddb.core.execution.run.types.AppRunTypeEnum;
import bio.cosy.feddb.core.security.ToolApiKeyAuthenticationMechanism;
import bio.cosy.feddb.core.security.ToolApiKeyService;
import bio.cosy.feddb.core.security.Scope;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.unihamburg.daibetes.api.app.author.FederatedAppAuthorBO;
import de.unihamburg.daibetes.api.auth.UserIdentity;
import de.unihamburg.daibetes.api.development.engine.TestEngineRunBO;
import de.unihamburg.daibetes.api.development.run.federated.FederatedParticipantUpdateDTO;
import de.unihamburg.daibetes.api.development.run.federated.FederatedRoundMessageCreateDTO;
import de.unihamburg.daibetes.api.development.run.federated.FederatedTestRunCreateDTO;
import de.unihamburg.daibetes.api.development.run.TestRunCreateDTO;
import de.unihamburg.daibetes.api.development.run.TestRunBO;
import de.unihamburg.daibetes.api.development.run.TestRunLauncher;
import de.unihamburg.daibetes.api.development.testembed.pydantic.PydanticUpdateDTO;
import io.quarkus.arc.log.LoggerName;
import io.quarkus.security.Authenticated;
import io.quarkus.websockets.next.*;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jboss.logging.Logger;

import java.util.List;

@WebSocket(path = "/testembed/{appid}/{type}")
@Authenticated
@Singleton
public class TestEmbedService {

    // The endpoint also receives errors after the WebSocket session context has been destroyed.
    // Keep the bean stateless and store authorization state on each connection instead.
    private static final UserData.TypedKey<Boolean> ACCEPTED = UserData.TypedKey.forBoolean("testembed.accepted");
    private static final UserData.TypedKey<Boolean> ENGINE_CONTAINER = UserData.TypedKey.forBoolean("testembed.engineContainer");

    @Inject
    ToolApiKeyService apiKeys;

    @Inject
    ObjectMapper objectMapper;

    private final List<AppMessageTypeEnum> DONT_FORWARD_DIRECTLY = List.of(AppMessageTypeEnum.START_RUN,
            AppMessageTypeEnum.CONFIG_CHANGED, AppMessageTypeEnum.UPDATE_RUN, AppMessageTypeEnum.LOG_MESSAGE,
            AppMessageTypeEnum.LOG_METRIC, AppMessageTypeEnum.NOTIFY_START_EXPERIMENT, AppMessageTypeEnum.CONFIG_INITIAL_SEND,
            AppMessageTypeEnum.START_FEDERATED_RUN, AppMessageTypeEnum.UPDATE_FEDERATED_RUN,
            AppMessageTypeEnum.FINISH_FEDERATED_RUN, AppMessageTypeEnum.FEDERATED_PARTICIPANT_UPDATE,
            AppMessageTypeEnum.FEDERATED_ROUND_MESSAGE);
    @Inject
    WebSocketConnection connection;
    @Inject
    TestEmbedBO testEmbedBO;
    @Inject
    TestRunBO testRunBO;
    @Inject
    TestRunLauncher testRunLauncher;
    @LoggerName("TestEmbedWebService")
    Logger logger;
    @Inject
    UserIdentity userIdentity;
    @Inject
    FederatedAppAuthorBO federatedAppAuthorBO;

    @Inject
    TestEngineRunBO testEngineRunBO;

    @Inject
    TestEmbedConnections connections;

    @OnOpen
    public AppMessageWrapperDTO<String> onOpen() {
        String keycloakId = userIdentity.getKeycloakId();
        String type = connection.pathParam("type");
        Long appId = Long.valueOf(connection.pathParam("appid"));
        boolean keyIdentity = userIdentity.getRoles().contains(ToolApiKeyAuthenticationMechanism.ROLE);
        boolean engineContainer = keyIdentity && apiKeys.isExecutionOwned(Scope.TEST_EMBED_APP, appId);
        connection.userData().put(ENGINE_CONTAINER, engineContainer);
        logger.infof("New connection to TestEmbedService for app %d and type %s", appId, type);
        if ((!AppConnectionTypesEnum.CLIENT.isEqual(type) && !AppConnectionTypesEnum.APP.isEqual(type))
                || (!keyIdentity && !federatedAppAuthorBO.isUserAuthor(keycloakId, appId))) {
            logger.warnf("Can not connect to TestEmbedService as User %s is not author of app %d", keycloakId, appId);
            connection.close(new CloseReason(1008, "User is not author of app"));
            return null;
        }
        if (AppConnectionTypesEnum.APP.isEqual(type) && !acceptEngine(appId, engineContainer)) {
            return null;
        }
        connection.userData().put(ACCEPTED, true);
        AppMessageWrapperDTO<String> dto = new AppMessageWrapperDTO<>(AppMessageTypeEnum.CLIENT_STARTED, "Type " + type + " connected");
        connections.of(appId, AppConnectionTypesEnum.oppositeOf(type)).forEach(c -> connections.sendTo(c, dto));
        handleClientConnected(appId, type);
        handleAppConnected(appId, type);
        return dto;
    }

    @OnError
    public void onError(WebSocketConnection connection, Throwable throwable) {
        String type = connection.pathParam("type");
        Long appId = Long.valueOf(connection.pathParam("appid"));

        if (TestEmbedConnections.isClosedFailure(throwable)) {
            logger.debugf("Testembed connection closed for app %d and type %s", appId, type);
        } else {
            logger.errorf(throwable, "Error in TestEmbedService for app %d and type %s", appId, type);
        }
    }

    @OnTextMessage
    <T> void consume(AppMessageWrapperDTO<T> m) {
        if (!Boolean.TRUE.equals(connection.userData().get(ACCEPTED))) {
            return;
        }
        Long appId = Long.valueOf(connection.pathParam("appid"));
        String type = connection.pathParam("type");
        AppRunTypeEnum runType = m.getRunType();
        try {
            if (AppConnectionTypesEnum.APP.isEqual(type) && !acceptsReportOfApp(m, appId, runType)) {
                logger.debugf("Ignoring late %s for app %d after its execution ended", m.getType(), appId);
                return;
            }
            logger.debug("Received message Text: " + m.getMessage() + " for app " + appId + " and type " + type);
            if (!DONT_FORWARD_DIRECTLY.contains(m.getType())) {
                connections.of(appId, AppConnectionTypesEnum.oppositeOf(type)).forEach(c -> connections.sendTo(c, m));
            }
            if (m.getType() == AppMessageTypeEnum.CONFIG_CHANGED) {
                handleConfiguration(m, appId, AppConnectionTypesEnum.APP.isEqual(type));
            } else if (AppConnectionTypesEnum.APP.isEqual(type)) {
                handleAppMessages(m, appId, runType);
            } else {
                handleClientMessages(m, appId);
            }
        } catch (Exception e) {
            AppMessageWrapperDTO<String> responseAppStarted = new AppMessageWrapperDTO<>(AppMessageTypeEnum.SERVER_ERROR,
                    e.getMessage());
            logger.error("Error handling message for app " + appId + " and type " + type, e);
            connections.sendTo(connection, responseAppStarted);
        }
    }

    @OnClose
    public void onClose() {
        if (!Boolean.TRUE.equals(connection.userData().remove(ACCEPTED))) {
            return;
        }
        Long appId = Long.valueOf(connection.pathParam("appid"));
        String type = connection.pathParam("type");
        AppMessageWrapperDTO<String> dto = new AppMessageWrapperDTO<>(AppMessageTypeEnum.CLIENT_STOPPED,
                "Type " + type + " disconnected",
                AppRunTypeEnum.NOT_DEFINED);
        connections.of(appId, AppConnectionTypesEnum.oppositeOf(type)).forEach(c -> connections.sendTo(c, dto));
        // The frontend learns about the ended test runs from the test run sender.
        if (AppConnectionTypesEnum.APP.isEqual(type)) {
            testRunBO.closeAllOnDisconnectTransactional(appId);
            if (Boolean.TRUE.equals(connection.userData().get(ENGINE_CONTAINER))) {
                testEngineRunBO.markDisconnectedTransactional(appId);
            }
        }
    }

    // A test engine container may only connect while it is started; a local engine not while one runs
    private boolean acceptEngine(Long appId, boolean engineContainer) {
        boolean containerStarted = testEngineRunBO.hasConnectingEngineTransactional(appId);
        if (engineContainer && !containerStarted) {
            connection.close(new CloseReason(1008, "No test engine is started for this app"));
            return false;
        }
        if (!engineContainer && containerStarted) {
            logger.warnf("Refused local engine of app %d while a test engine container runs", appId);
            connection.close(new CloseReason(1008, "A test engine container runs for this app, stop it first"));
            return false;
        }
        if (engineContainer) {
            testEngineRunBO.markConnectedTransactional(appId);
        }
        return true;
    }

    private void handleClientConnected(Long appId, String type) {
        if (!AppConnectionTypesEnum.CLIENT.isEqual(type)) {
            return;
        }
        if (connections.of(appId, AppConnectionTypesEnum.APP).findAny().isPresent()) {
            AppMessageWrapperDTO<String> responseAppStarted = new AppMessageWrapperDTO<>(AppMessageTypeEnum.CLIENT_STARTED,
                    "app is connected");
            connections.sendTo(connection, responseAppStarted);
        }
    }

    private void handleAppConnected(Long appId, String type) {
        if (!AppConnectionTypesEnum.APP.isEqual(type)) {
            return;
        }
        testRunLauncher.launchPending(appId);
        testEmbedBO.findAppConfigTransactional(appId).ifPresent(config -> {
            connections.of(appId, AppConnectionTypesEnum.APP).forEach(c -> connections.sendTo(c, config));
        });
    }

    private <T> void handleConfiguration(AppMessageWrapperDTO<T> message, Long appId, boolean fromApp) {
        FederatedAppDetailDTO config = objectMapper.convertValue(message.getMessage(), FederatedAppDetailDTO.class);
        AppMessageWrapperDTO<FederatedAppDetailDTO> updated = testEmbedBO.saveAppConfigTransactional(config, appId, fromApp);
        if (updated.getType() == AppMessageTypeEnum.DO_NOTHING) {
            return;
        }
        if (!fromApp) {
            connections.of(appId, AppConnectionTypesEnum.APP).forEach(c -> connections.sendTo(c, updated));
        }
        AppMessageWrapperDTO<PydanticUpdateDTO> pydantic = testEmbedBO.pydanticObjOf(updated.getMessage());
        connections.of(appId, AppConnectionTypesEnum.CLIENT).forEach(c -> {
            connections.sendTo(c, pydantic);
            if (fromApp) {
                connections.sendTo(c, updated);
            }
        });
    }

    private <T> void handleClientMessages(AppMessageWrapperDTO<T> m, Long appId) {
        switch (m.getType()) {
            case START_RUN:
                // The test run launcher hands it to the app, the test run sender shows it to the frontend.
                TestRunCreateDTO createDTO = objectMapper.convertValue(m.getMessage(), TestRunCreateDTO.class);
                testRunBO.startTestTransactional(appId, createDTO, userIdentity.getKeycloakId());
                break;
            case START_FEDERATED_RUN:
                FederatedTestRunCreateDTO fedCreate = objectMapper.convertValue(m.getMessage(), FederatedTestRunCreateDTO.class);
                testRunBO.startFederatedTestTransactional(appId, fedCreate, userIdentity.getKeycloakId());
                break;
            case NOTIFY_START_EXPERIMENT:
                testRunLauncher.launchPending(appId);
            default:
                break;

        }
    }

    <T> boolean acceptsReportOfApp(AppMessageWrapperDTO<T> m, Long appId, AppRunTypeEnum runType) {
        // A development engine serves many executions, but only those of its authenticated app.
        String identityField = switch (m.getType()) {
            case UPDATE_RUN, FINISH_RUN, LOG_MESSAGE, LOG_METRIC -> "runId";
            case UPDATE_FEDERATED_RUN, FINISH_FEDERATED_RUN -> "id";
            case FEDERATED_PARTICIPANT_UPDATE, FEDERATED_ROUND_MESSAGE -> "federatedRunId";
            default -> null;
        };
        if (identityField != null) {
            var id = objectMapper.valueToTree(m.getMessage()).get(identityField);
            boolean federated = !"runId".equals(identityField) || (runType != null && runType.isFederated());
            return testRunBO.acceptsReportOfAppTransactional(appId,
                    id != null && id.canConvertToLong() ? id.longValue() : null, federated);
        }
        return true;
    }

    private <T> void handleAppMessages(AppMessageWrapperDTO<T> m, Long appId, AppRunTypeEnum runType) {
        switch (m.getType()) {
            case CLIENT_STARTED:
                break;
            case SEND_MODEL:
                logger.warn("Received model upload, use HTTP endpoint instead");
                break;
            case UPDATE_RUN:
                UpdateRunDTO updateTest = objectMapper.convertValue(m.getMessage(), UpdateRunDTO.class);
                testRunBO.updateRunTransactional(updateTest, runType);
                break;
            case FINISH_RUN:
                FinishRunDTO finishTest = objectMapper.convertValue(m.getMessage(), FinishRunDTO.class);
                // The next trial of an experiment is launched by its step coordinator.
                testRunBO.finishRunTransactional(finishTest, runType);
                break;
            case LOG_MESSAGE:
                ExecutionMessageLogDTO runMessage = objectMapper.convertValue(m.getMessage(), ExecutionMessageLogDTO.class);
                AppMessageWrapperDTO<ExecutionMessageLogDTO> savedLog = testEmbedBO.logTestMessageTransactional(runMessage.getRunId(), runMessage, runType);
                connections.of(appId, AppConnectionTypesEnum.CLIENT).forEach(c -> connections.sendTo(c, savedLog));
                break;
            case LOG_METRIC:
                ExecutionMessageMetricDTO runMetric = objectMapper.convertValue(m.getMessage(), ExecutionMessageMetricDTO.class);
                AppMessageWrapperDTO<ExecutionMessageMetricDTO> savedMetric = testEmbedBO.logTestMetricTransactional(runMetric.getRunId(), runMetric, runType);
                connections.of(appId, AppConnectionTypesEnum.CLIENT).forEach(c -> connections.sendTo(c, savedMetric));
                break;
            case UPDATE_FEDERATED_RUN:
            case FINISH_FEDERATED_RUN:
                // The app sends its whole run, only status, error and round are taken from it.
                FederatedRunLifecycleDTO fedUpdate = objectMapper.convertValue(m.getMessage(), FederatedRunLifecycleDTO.class);
                testRunBO.updateFederatedRunTransactional(fedUpdate);
                break;
            case FEDERATED_PARTICIPANT_UPDATE:
                FederatedParticipantUpdateDTO pUpdate = objectMapper.convertValue(m.getMessage(), FederatedParticipantUpdateDTO.class);
                testRunBO.updateParticipantTransactional(pUpdate);
                break;
            case FEDERATED_ROUND_MESSAGE:
                FederatedRoundMessageCreateDTO rmCreate = objectMapper.convertValue(m.getMessage(), FederatedRoundMessageCreateDTO.class);
                AppMessageWrapperDTO<?> round = testEmbedBO.logFederatedRoundMessage(rmCreate);
                connections.of(appId, AppConnectionTypesEnum.CLIENT).forEach(c -> connections.sendTo(c, round));
                break;
            default:
                break;
        }

    }
}
