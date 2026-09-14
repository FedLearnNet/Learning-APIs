package de.unihamburg.daibetes.api.testembed;

import bio.cosy.feddb.core.api.app.FederatedAppDetailDTO;
import bio.cosy.feddb.core.api.run.*;
import bio.cosy.feddb.core.api.run.message.RunMessageLogDTO;
import bio.cosy.feddb.core.api.run.message.RunMessageMetricDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.unihamburg.daibetes.api.app.author.FederatedAppAuthorBO;
import de.unihamburg.daibetes.api.auth.UserIdentity;
import de.unihamburg.daibetes.api.runs.test.federated.participant.FederatedParticipantUpdateDTO;
import de.unihamburg.daibetes.api.runs.test.federated.message.FederatedRoundMessageCreateDTO;
import de.unihamburg.daibetes.api.runs.test.federated.FederatedTestRunCreateDTO;
import de.unihamburg.daibetes.api.runs.test.federated.FederatedTestRunDTO;
import de.unihamburg.daibetes.api.runs.test.TestRunCreateDTO;
import de.unihamburg.daibetes.api.runs.test.TestRunDTO;
import de.unihamburg.daibetes.api.testembed.pydantic.PydanticUpdateDTO;
import io.quarkus.arc.log.LoggerName;
import io.quarkus.security.Authenticated;
import io.quarkus.websockets.next.*;
import jakarta.inject.Inject;
import org.apache.commons.lang3.StringUtils;
import org.jboss.logging.Logger;

import java.util.List;
import java.util.stream.Stream;

@WebSocket(path = "/testembed/{appid}/{type}")
@Authenticated
public class TestEmbedService {

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
    @LoggerName("TestEmbedWebService")
    Logger logger;
    @Inject
    UserIdentity userIdentity;
    @Inject
    FederatedAppAuthorBO federatedAppAuthorBO;

    @OnOpen
    public AppMessageWrapperDTO<String> onOpen() {
        String keycloakId = userIdentity.getKeycloakId();
        String type = connection.pathParam("type");
        Long appId = Long.valueOf(connection.pathParam("appid"));
        logger.infof("New connection to TestEmbedService for app %d and type %s", appId, type);
        if (StringUtils.isNotEmpty(keycloakId) &&
                type.equals(AppConnectionTypesEnum.CLIENT.name()) &&
                !federatedAppAuthorBO.isUserAuthor(userIdentity.getKeycloakId(), appId)) {
            logger.warnf("Can not connect to TestEmbedService as User %s is not author of app %d", keycloakId, appId);
            connection.close(new CloseReason(403, "User is not author of app"));
            return null;
        }//TODO ELSE
        AppMessageWrapperDTO<String> dto = new AppMessageWrapperDTO<>(AppMessageTypeEnum.CLIENT_STARTED, "Type " + type + " connected");
        getConnections(appId, AppConnectionTypesEnum.getOpposite(type)).forEach(c -> c.sendTextAndAwait(dto));
        handleClientConnected(appId, type);
        handleAppConnected(appId, type);
        return dto;
    }

    @OnError
    public void onError(WebSocketConnection connection, Throwable throwable) {
        String type = connection.pathParam("type");
        Long appId = Long.valueOf(connection.pathParam("appid"));

        logger.errorf("Error in TestEmbedService for app %d and type %s: %s", appId, type, throwable.getMessage());
    }

    @OnTextMessage
    <T> void consume(AppMessageWrapperDTO<T> m) {
        Long appId = Long.valueOf(connection.pathParam("appid"));
        String type = connection.pathParam("type");
        AppRunTypeEnum runType = m.getRunType();
        try {
            logger.debug("Received message Text: " + m.getMessage() + " for app " + appId + " and type " + type);
            if (!DONT_FORWARD_DIRECTLY.contains(m.getType())) {
                getConnections(appId, AppConnectionTypesEnum.getOpposite(type)).forEach(c -> c.sendTextAndAwait(m));
            }
            if (AppConnectionTypesEnum.APP.isEqual(type)) {
                handleAppMessages(m, appId, type, runType);
            }
            if (AppConnectionTypesEnum.CLIENT.isEqual(type)) {
                handleClientMessages(m, appId, type, runType);
            }
        } catch (Exception e) {
            e.printStackTrace();
            AppMessageWrapperDTO<String> responseAppStarted = new AppMessageWrapperDTO<>(AppMessageTypeEnum.SERVER_ERROR,
                    e.getMessage());
            connection.sendTextAndAwait(responseAppStarted);
            logger.error("Error handling message for app " + appId + " and type " + type, e);
        }
    }

    @OnClose
    public void onClose() {
        Long appId = Long.valueOf(connection.pathParam("appid"));
        String type = connection.pathParam("type");
        AppMessageWrapperDTO<String> dto = new AppMessageWrapperDTO<>(AppMessageTypeEnum.CLIENT_STOPPED,
                "Type " + type + " disconnected",
                AppRunTypeEnum.NOT_DEFINED);
        getConnections(appId, AppConnectionTypesEnum.getOpposite(type)).forEach(c -> c.sendTextAndAwait(dto));
        if (AppConnectionTypesEnum.APP.isEqual(type)) {
            List<AppMessageWrapperDTO<TestRunDTO>> closed = testEmbedBO.closeAllTest(appId);
            getConnections(appId, AppConnectionTypesEnum.CLIENT).forEach(c -> closed.forEach(c::sendTextAndAwait));
            List<AppMessageWrapperDTO<FederatedTestRunDTO>> closedFed = testEmbedBO.closeAllFederatedOnDisconnect(appId);
            getConnections(appId, AppConnectionTypesEnum.CLIENT).forEach(c -> closedFed.forEach(c::sendTextAndAwait));
        }
    }

    private void handleClientConnected(Long appId, String type) {
        if (!AppConnectionTypesEnum.CLIENT.isEqual(type)) {
            return;
        }
        if (getConnections(appId, AppConnectionTypesEnum.APP).findAny().isPresent()) {
            AppMessageWrapperDTO<String> responseAppStarted = new AppMessageWrapperDTO<>(AppMessageTypeEnum.CLIENT_STARTED,
                    "app is connected");
            connection.sendTextAndAwait(responseAppStarted);
        }
    }

    private void handleAppConnected(Long appId, String type) {
        if (!AppConnectionTypesEnum.APP.isEqual(type)) {
            return;
        }
        testEmbedBO.getNextExperimentRun(appId).ifPresent(run -> {
            getConnections(appId, AppConnectionTypesEnum.APP).forEach(c -> c.sendTextAndAwait(run));
        });
        testEmbedBO.getAppConfig(appId).ifPresent(config -> {
            getConnections(appId, AppConnectionTypesEnum.APP).forEach(c -> c.sendTextAndAwait(config));
        });
    }

    private <T> void handleClientMessages(AppMessageWrapperDTO<T> m, Long appId, String type, AppRunTypeEnum runType) {
        ObjectMapper objectMapper = new ObjectMapper();

        switch (m.getType()) {
            case CONFIG_CHANGED:
                // handle pydantic class generation
                FederatedAppDetailDTO config = objectMapper.convertValue(m.getMessage(), FederatedAppDetailDTO.class);
                AppMessageWrapperDTO<FederatedAppDetailDTO> updatedConfig = testEmbedBO.saveAppConfig(config, appId, false);
                if (updatedConfig.getType().equals(AppMessageTypeEnum.DO_NOTHING)) {
                    return;
                }
                getConnections(appId, AppConnectionTypesEnum.APP).forEach(c -> c.sendTextAndAwait(updatedConfig));
                AppMessageWrapperDTO<PydanticUpdateDTO> pydanticDto = testEmbedBO.getPydanticObj(updatedConfig.getMessage());
                getConnections(appId, AppConnectionTypesEnum.CLIENT).forEach(c -> c.sendTextAndAwait(pydanticDto));
                break;
            case START_RUN:
                TestRunCreateDTO createDTO = objectMapper.convertValue(m.getMessage(), TestRunCreateDTO.class);
                AppMessageWrapperDTO<StartRunDTO> response = testEmbedBO.startTest(appId, createDTO, "TODO");
                getConnections(appId).forEach(c -> c.sendTextAndAwait(response));
                this.connection.sendTextAndAwait(response);
                break;
            case START_FEDERATED_RUN:
                FederatedTestRunCreateDTO fedCreate = objectMapper.convertValue(m.getMessage(), FederatedTestRunCreateDTO.class);
                AppMessageWrapperDTO<FederatedTestRunDTO> fedResponse = testEmbedBO.startFederatedTest(appId, fedCreate);
                // broadcast to both APP and CLIENT: APP needs it to execute, CLIENT to update list
                getConnections(appId).forEach(c -> c.sendTextAndAwait(fedResponse));
                break;
            case NOTIFY_START_EXPERIMENT:
                testEmbedBO.getNextExperimentRun(appId).ifPresent(run -> {
                    getConnections(appId, AppConnectionTypesEnum.APP).forEach(c -> c.sendTextAndAwait(run));
                });
            default:
                break;

        }
    }

    private <T> void handleAppMessages(AppMessageWrapperDTO<T> m, Long appId, String type, AppRunTypeEnum runType) {
        ObjectMapper objectMapper = new ObjectMapper();

        switch (m.getType()) {
            case CONFIG_CHANGED:
                // handle pydantic class generation
                FederatedAppDetailDTO config = objectMapper.convertValue(m.getMessage(), FederatedAppDetailDTO.class);
                AppMessageWrapperDTO<FederatedAppDetailDTO> updatedConfig = testEmbedBO.saveAppConfig(config, appId, true);
                if (updatedConfig.getType().equals(AppMessageTypeEnum.DO_NOTHING)) {
                    return;
                }
                AppMessageWrapperDTO<PydanticUpdateDTO> pydanticDto = testEmbedBO.getPydanticObj(updatedConfig.getMessage());
                getConnections(appId, AppConnectionTypesEnum.CLIENT).forEach(c -> c.sendTextAndAwait(pydanticDto));
                getConnections(appId, AppConnectionTypesEnum.CLIENT).forEach(c -> c.sendTextAndAwait(updatedConfig));
                break;
            case CLIENT_STARTED:
                break;
            case SEND_MODEL:
                logger.warn("Received model upload, use HTTP endpoint instead");
                break;
            case UPDATE_RUN:
                UpdateRunDTO updateTest = objectMapper.convertValue(m.getMessage(), UpdateRunDTO.class);
                AppMessageWrapperDTO<?> updatedTest = testEmbedBO.updateRun(updateTest, runType);
                getConnections(appId, AppConnectionTypesEnum.CLIENT).forEach(c -> c.sendTextAndAwait(updatedTest));
                break;
            case FINISH_RUN:
                FinishRunDTO finishTest = objectMapper.convertValue(m.getMessage(), FinishRunDTO.class);
                AppMessageWrapperDTO<?> finishedTest = testEmbedBO.finishRun(finishTest, runType);
                getConnections(appId, AppConnectionTypesEnum.CLIENT).forEach(c -> c.sendTextAndAwait(finishedTest));
                finishedTest.setType(AppMessageTypeEnum.UPDATE_RUN);
                getConnections(appId, AppConnectionTypesEnum.CLIENT).forEach(c -> c.sendTextAndAwait(finishedTest));
                handleAppConnected(appId, type);
                break;
            case LOG_MESSAGE:
                RunMessageLogDTO runMessage = objectMapper.convertValue(m.getMessage(), RunMessageLogDTO.class);
                AppMessageWrapperDTO<RunMessageLogDTO> savedLog = testEmbedBO.logTestMessage(runMessage.getRunId(), runMessage, runType);
                getConnections(appId, AppConnectionTypesEnum.CLIENT).forEach(c -> c.sendTextAndAwait(savedLog));
                break;
            case LOG_METRIC:
                RunMessageMetricDTO runMetric = objectMapper.convertValue(m.getMessage(), RunMessageMetricDTO.class);
                AppMessageWrapperDTO<RunMessageMetricDTO> savedMetric = testEmbedBO.logTestMetric(runMetric.getRunId(), runMetric, runType);
                getConnections(appId, AppConnectionTypesEnum.CLIENT).forEach(c -> c.sendTextAndAwait(savedMetric));
                break;
            case UPDATE_FEDERATED_RUN:
            case FINISH_FEDERATED_RUN:
                FederatedTestRunDTO fedUpdate = objectMapper.convertValue(m.getMessage(), FederatedTestRunDTO.class);
                AppMessageWrapperDTO<FederatedTestRunDTO> fedUpdateResponse = testEmbedBO.updateFederatedRun(fedUpdate);
                if (m.getType() == AppMessageTypeEnum.FINISH_FEDERATED_RUN) {
                    fedUpdateResponse.setType(AppMessageTypeEnum.FINISH_FEDERATED_RUN);
                }
                getConnections(appId, AppConnectionTypesEnum.CLIENT).forEach(c -> c.sendTextAndAwait(fedUpdateResponse));
                break;
            case FEDERATED_PARTICIPANT_UPDATE:
                FederatedParticipantUpdateDTO pUpdate = objectMapper.convertValue(m.getMessage(), FederatedParticipantUpdateDTO.class);
                getConnections(appId, AppConnectionTypesEnum.CLIENT).forEach(c -> c.sendTextAndAwait(testEmbedBO.updateFederatedParticipant(pUpdate)));
                break;
            case FEDERATED_ROUND_MESSAGE:
                FederatedRoundMessageCreateDTO rmCreate = objectMapper.convertValue(m.getMessage(), FederatedRoundMessageCreateDTO.class);
                getConnections(appId, AppConnectionTypesEnum.CLIENT).forEach(c -> c.sendTextAndAwait(testEmbedBO.logFederatedRoundMessage(rmCreate)));
                break;
            default:
                break;
        }

    }


    private Stream<WebSocketConnection> getConnections(Long appId) {
        return connection.getOpenConnections().stream()
                .filter(c -> c.endpointId().equals(this.getClass().getCanonicalName()))
                .filter(c -> c.pathParam("appid").equalsIgnoreCase(appId.toString()));
    }

    private Stream<WebSocketConnection> getConnections(Long appId, AppConnectionTypesEnum type) {
        return getConnections(appId)
                .filter(c -> type.isEqual(c.pathParam("type")));
    }

    public void sendPublishedUpdate(FederatedAppDetailDTO app) {
        AppMessageWrapperDTO<FederatedAppDetailDTO> updateTestEmbed = new AppMessageWrapperDTO<>(AppMessageTypeEnum.CONFIG_CHANGED, app);

        getConnections(app.getId()).forEach(c -> c.sendTextAndAwait(updateTestEmbed));
    }

}
