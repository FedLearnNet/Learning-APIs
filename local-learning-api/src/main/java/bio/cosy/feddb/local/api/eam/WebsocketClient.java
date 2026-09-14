package bio.cosy.feddb.local.api.eam;

import bio.cosy.feddb.core.api.query.QueryClientResponseDTO;
import bio.cosy.feddb.core.api.query.QueryDTO;
import bio.cosy.feddb.core.api.socket.*;
import bio.cosy.feddb.local.api.learning.project.FederatedLearningSyncBO;
import bio.cosy.feddb.local.api.learning.request.FederatedLearningRequestBO;
import bio.cosy.feddb.local.api.learning.request.metrics.RequestRunMetricsBO;
import bio.cosy.feddb.local.api.query.QueryBO;
import bio.cosy.feddb.local.api.statistics.request.RequestDataStatisticsBO;
import bio.cosy.feddb.local.health.WebSocketClientState;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.arc.log.LoggerName;
import io.quarkus.logging.Log;
import io.quarkus.websockets.next.*;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.List;
import java.util.Optional;

@WebSocketClient(path = "/query/clients")
public class WebsocketClient {

    @LoggerName("GlobalWebsocketClient")
    Logger logger;

    @Inject
    QueryBO queryBO;

    @Inject
    FederatedLearningRequestBO federatedLearningRequestBO;

    @Inject
    FederatedLearningSyncBO federatedLearningSyncBO;

    @Inject
    WebSocketClientState state;

    @Inject
    RequestDataStatisticsBO requestDataStatisticsBO;

    @Inject
    RequestRunMetricsBO requestRunMetricsBO;

    @Inject
    ClientManager clientManager;

    @OnOpen
    public void onOpen(WebSocketClientConnection connection) {
        logger.info("Connection opened: " + connection.id());
        FedDBClientDataDTO<CurrentLearningClientSyncResponseDTO> response = sendCurrentLearnings();
        connection.sendText(response)
                .subscribe().with(
                        unused -> {
                            clientManager.connectionOpened(connection);
                            logger.info("Initial CURRENT_LEARNINGS sync sent to server.");
                            state.markConnected();
                        },
                        failure -> {
                            logger.error("Failed to send initial CURRENT_LEARNINGS sync", failure);
                            state.markError(failure.getMessage());
                            state.markDisconnected();
                        }
                );
    }

    @OnClose
    public void onClose(WebSocketClientConnection connection) {
        CloseReason reason = connection.closeReason();
        logger.infof("Global WebSocket closed: connectionId=%s, openedAt=%s, closeCode=%s, closeReason=%s",
                connection.id(), connection.creationTime(), reason != null ? reason.getCode() : "unavailable",
                reason != null && reason.getMessage() != null
                        ? reason.getMessage().replaceAll("[\\r\\n\\t]", " ") : "not provided");
        if (reason != null && reason.getCode() == 1008) {
            logger.warnf("Global WebSocket closed for policy violation: connectionId=%s; check server logs for authentication/authorization or other policy failures",
                    connection.id());
        }
        state.markDisconnected();
    }

    @OnError
    public void onError(WebSocketClientConnection connection, Throwable throwable) {
        logger.errorf("Global WebSocket error: connectionId=%s; %s",
                connection != null ? connection.id() : "unavailable", EamLogDetails.failure(throwable));
        state.markError(throwable.getMessage());
        state.markDisconnected();
    }

    @OnTextMessage
    public <T> Multi<Object> onMessage(Multi<FedDBClientDataDTO<T>> message) {
        return message
                .emitOn(Infrastructure.getDefaultWorkerPool())
                .onItem().transform(m -> {
                    state.markMessage();
                    return processMessage(m);
                })
                //.ifNoItem().after(Duration.ofSeconds(30)).fail()
                .onFailure().invoke(t -> {
                    logger.error("Error in message processing: {}", t.getMessage(), t);
                    state.markError(t.getMessage());
                });

    }


    private <T> Object processMessage(FedDBClientDataDTO<T> m) {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            Log.info("Received global message message: " + m.getMessageType());
            if (m.getMessageType().equals(FedDBClientTypeEnum.EXISTING_QUERY)) {
                QueryDTO query = objectMapper.convertValue(m.getMessage(), QueryDTO.class);
                QueryClientResponseDTO responseData = queryBO.handleQuery(query);
                FedDBClientDataDTO<QueryClientResponseDTO> response = new FedDBClientDataDTO<>();
                response.setMessageType(FedDBClientTypeEnum.EXISTING_QUERY);
                response.setMessage(responseData);
                Log.info("Sending query response: " + responseData);
                return response;
            }
            if (m.getMessageType().equals(FedDBClientTypeEnum.DATA_STATISTICS)) {
                ProjectFederatedRequestDataStatisticsDTO query = objectMapper.convertValue(m.getMessage(), ProjectFederatedRequestDataStatisticsDTO.class);
                requestDataStatisticsBO.createAndCheckAutoAccessTransactional(query);
                FedDBClientDataDTO<Object> response = new FedDBClientDataDTO<>();
                response.setMessageType(FedDBClientTypeEnum.NO_RESPONSE);
                Log.info("Sending data statistics response");
                return response;
            }

            if (m.getMessageType().equals(FedDBClientTypeEnum.LEARNING_QUERY)) {
                ProjectFederatedExperimentForLocalDTO requestLearning = objectMapper.convertValue(m.getMessage(), ProjectFederatedExperimentForLocalDTO.class);
                Optional<LearningQueryClientResponseDTO> responseData = federatedLearningRequestBO.handleLearningRequest(requestLearning);
                //if Successful, response will be asychronously sent to the client, so here only error handling
                if (responseData.isPresent()) {
                    FedDBClientDataDTO<LearningQueryClientResponseDTO> response = new FedDBClientDataDTO<>();
                    response.setMessageType(FedDBClientTypeEnum.LEARNING_QUERY);
                    response.setMessage(responseData.get());
                    Log.info("Sending learning query response: " + responseData.get());
                    return response;
                } else {
                    //SUCCESSFUL HANDLING, NO RESPONSE NEEDED
                    FedDBClientDataDTO<Object> response = new FedDBClientDataDTO<>();
                    response.setMessageType(FedDBClientTypeEnum.NO_RESPONSE);
                    return response;
                }
            }
            if (m.getMessageType().equals(FedDBClientTypeEnum.START_LEARNING)) {
                StartLearningClientRequestDTO startLearning = objectMapper.convertValue(m.getMessage(), StartLearningClientRequestDTO.class);

                Optional<LearningClientSyncResponseDTO> responseData = federatedLearningSyncBO.handleStartLearningRequest(startLearning);
                if (responseData.isPresent()) {
                    FedDBClientDataDTO<LearningClientSyncResponseDTO> response = new FedDBClientDataDTO<>();
                    response.setMessageType(FedDBClientTypeEnum.UPDATE_LEARNING);
                    response.setMessage(responseData.get());
                    return response;
                }

            }
            if (m.getMessageType().equals(FedDBClientTypeEnum.UPDATE_LEARNING)) {
                LearningClientSyncRequestDTO startLearning = objectMapper.convertValue(m.getMessage(), LearningClientSyncRequestDTO.class);

                Optional<LearningClientSyncResponseDTO> responseData = federatedLearningSyncBO.handleNextStep(startLearning);
                if (responseData.isPresent()) {
                    FedDBClientDataDTO<LearningClientSyncResponseDTO> response = new FedDBClientDataDTO<>();
                    response.setMessageType(FedDBClientTypeEnum.UPDATE_LEARNING);
                    response.setMessage(responseData.get());
                    return response;
                } else {
                    // Successful sync commands continue asynchronously through websocket status updates.
                    return noResponse();
                }
            }
            if (m.getMessageType().equals(FedDBClientTypeEnum.STOP_LEARNING)) {
                LearningClientStopRequestDTO stopLearning = objectMapper.convertValue(m.getMessage(), LearningClientStopRequestDTO.class);
                federatedLearningSyncBO.stopLearning(stopLearning);
                return sendCurrentLearnings();
            }
            if (m.getMessageType().equals(FedDBClientTypeEnum.RUN_METRICS)) {
                ProjectFederatedRequestRunMetricsDTO request = objectMapper.convertValue(m.getMessage(), ProjectFederatedRequestRunMetricsDTO.class);
                requestRunMetricsBO.createAndCheckAutoAccessTransactional(request);
                return noResponse();
            }
        } catch (Exception e) {
            logger.error("Error in EAM Websocket: " + e.getMessage());
            e.printStackTrace();
            FedDBClientDataDTO<Object> response = new FedDBClientDataDTO<>();
            response.setMessageType(FedDBClientTypeEnum.ERROR);
            return response;
        }
        // There is always at least a NO_RESPONSE like response, so if we reach this part
        // Then there was some error in the message handling
        logger.error("Could not process message: " + m.getMessageType() + " With content: " + m.getMessage());
        return noResponse();
    }

    private FedDBClientDataDTO<Object> noResponse() {
        FedDBClientDataDTO<Object> response = new FedDBClientDataDTO<>();
        response.setMessageType(FedDBClientTypeEnum.NO_RESPONSE);
        return response;
    }

    private FedDBClientDataDTO<CurrentLearningClientSyncResponseDTO> sendCurrentLearnings() {
        List<String> currentGlobalLearningIds;
        try {
            currentGlobalLearningIds = federatedLearningRequestBO.findApprovedAndRunningIds();
        } catch (Exception e) {
            logger.warnf("Falling back to empty CURRENT_LEARNINGS sync: %s", e.getMessage());
            state.markError("CURRENT_LEARNINGS fallback: " + e.getMessage());
            currentGlobalLearningIds = List.of();
        }
        CurrentLearningClientSyncResponseDTO syncResponse = CurrentLearningClientSyncResponseDTO.createResponse(currentGlobalLearningIds);
        FedDBClientDataDTO<CurrentLearningClientSyncResponseDTO> response = new FedDBClientDataDTO<>();
        response.setMessageType(FedDBClientTypeEnum.CURRENT_LEARNINGS);
        response.setMessage(syncResponse);
        return response;
    }
}
