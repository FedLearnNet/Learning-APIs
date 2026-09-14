package de.unihamburg.daibetes.api.feddbclient;

import bio.cosy.feddb.core.api.query.QueryDTO;
import bio.cosy.feddb.core.api.socket.*;
import de.unihamburg.daibetes.api.observer.FLNetClientObserverEmitter;
import de.unihamburg.daibetes.api.observer.FLNetClientObserverEventDTO;
import io.quarkus.arc.log.LoggerName;
import io.quarkus.websockets.next.Connection;
import io.quarkus.websockets.next.OpenConnections;
import jakarta.annotation.Nullable;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Manages WebSocket connections and message broadcasting for federated learning experiments.
 * This class handles the distribution of queries and learning-related messages to connected clients,
 * maintains connection-experiment mappings, and coordinates the learning process across participants.
 */
@ApplicationScoped
public class FLNetClientBroadcastBO {

    @Inject
    OpenConnections connections;

    @Inject
    FLNetClientObserverEmitter observerEmitter;

    @LoggerName("FedDBClientBroadcast")
    Logger logger;

    private static final String ENDPOINT_ID = FLNetClientWebsocket.class.getName();


    private final Map<String, Set<String>> learningConnectionMap = new ConcurrentHashMap<>();

    /**
     * Associates a WebSocket connection with a specific learning experiment.
     *
     * @param connectionId The unique identifier of the WebSocket connection
     * @param response     The identifier of the learning experiments
     */
    public void syncLearningConnection(String connectionId, CurrentLearningClientSyncResponseDTO response) {
        if (connectionId == null || response == null) {
            logger.warnf("ConnectionId or ExperimentId is null. ConnectionId: %s, ExperimentIds: %s", connectionId, response);
            return;
        }
        List<String> experimentIds = response.getCurrentGlobalUqLearningIds();
        if (experimentIds == null || experimentIds.isEmpty()) {
            logger.warnf("No experiments found for ConnectionId: %s", connectionId);
            removeLearningConnection(connectionId);
            return;
        }
        learningConnectionMap.computeIfAbsent(connectionId, k -> new CopyOnWriteArraySet<>()).addAll(experimentIds);
        logger.infof("Added learning connections for ConnectionId: %s, ExperimentIds: %s", connectionId, experimentIds);
    }

    /**
     * Removes all experiment associations for a given WebSocket connection.
     * Called when a client disconnects or needs to be disassociated from all experiments.
     *
     * @param connectionId The unique identifier of the WebSocket connection to remove
     */
    private void removeLearningConnection(String connectionId) {
        if (connectionId == null) {
            logger.warnf("ConnectionId is null");
            return;
        }
        learningConnectionMap.remove(connectionId);
        logger.infof("Removed learning connection for ConnectionId: %s", connectionId);
    }

    /**
     * Removes a specific experiment association from a WebSocket connection.
     * If this was the last experiment for the connection, the connection entry is removed entirely.
     *
     * @param connectionId The unique identifier of the WebSocket connection
     * @param experimentId The identifier of the experiment to remove
     */
    public void removeLearningExperiment(String connectionId, String experimentId) {
        if (connectionId == null || experimentId == null) {
            logger.warnf("ConnectionId or ExperimentId is null. ConnectionId: %s, ExperimentId: %s", connectionId, experimentId);
            return;
        }
        Set<String> experiments = learningConnectionMap.get(connectionId);
        if (experiments != null) {
            experiments.remove(experimentId);
            logger.infof("Removed experiment %d from connection %s", experimentId, connectionId);
            if (experiments.isEmpty()) {
                learningConnectionMap.remove(connectionId);
                logger.infof("Removed connection %s as no experiments remain", connectionId);
            }
        }
    }

    /**
     * Broadcasts an existing query to all connected clients.
     * Used for distributing queries that need to be processed by all participants.
     *
     * @param query The query to be distributed to all connected clients
     */
    public void fireExistingQuery(QueryDTO query) {
        logger.infof("Firing existing query: %s", query);
        FedDBClientDataDTO<QueryDTO> fedDBClientDataDTO = new FedDBClientDataDTO<>(
                FedDBClientTypeEnum.EXISTING_QUERY, query);

        sendMessageToAll(fedDBClientDataDTO);
    }

    /**
     * Broadcasts a learning experiment configuration to all connected clients.
     * Initiates the setup phase of a federated learning experiment across all participants.
     *
     * @param experiment The experiment configuration to be distributed
     */
    public void fireLearningQuery(ProjectFederatedExperimentForLocalDTO experiment) {
        logger.infof("Firing learning query for experiment: %s", experiment);
        FedDBClientDataDTO<ProjectFederatedExperimentForLocalDTO> fedDBClientDataDTO = new FedDBClientDataDTO<>(
                FedDBClientTypeEnum.LEARNING_QUERY, experiment);

        sendMessageToAll(fedDBClientDataDTO);
    }

    /**
     * Broadcasts a run-metrics request to all connected clients.
     * Asks each participating clinic to return their local training metrics for the given experiment.
     *
     * @param request the metrics request details (keycloakId, experiment uniqueId, globalRequestId)
     */
    public void fireRunMetricsRequest(ProjectFederatedRequestRunMetricsDTO request) {
        logger.infof("Requesting run metrics for experiment: %s", request.getGlobalExperimentUniqueId());
        FedDBClientDataDTO<ProjectFederatedRequestRunMetricsDTO> fedDBClientDataDTO = new FedDBClientDataDTO<>(
                FedDBClientTypeEnum.RUN_METRICS, request);

        sendMessageToExperiment(fedDBClientDataDTO, request.getGlobalExperimentUniqueId());
    }

    /**
     * Broadcasts a data statistics query to all connected clients.
     * Used to gather data-related information from all participants before or during a federated learning experiment
     *
     * @param request the needed information for the client
     */
    public void fireDataStatisticsQuery(ProjectFederatedRequestDataStatisticsDTO request) {
        logger.infof("Request data statistics for learning: %s", request.getGlobalUniqueQueryId());
        FedDBClientDataDTO<ProjectFederatedRequestDataStatisticsDTO> fedDBClientDataDTO = new FedDBClientDataDTO<>(
                FedDBClientTypeEnum.DATA_STATISTICS, request);

        sendMessageToAll(fedDBClientDataDTO);
    }

    /**
     * This method is used to start the learning process for a federated experiment
     * Sends a message to all clients participating in this experiment to start learning
     *
     * @param globalUniqueExperimentId the id of the experiment to start learning
     * @param uniqueRandomClinicId     the unique random clinic id of the participant (can be Null)
     */
    public void startLearning(String globalUniqueExperimentId, @Nullable String uniqueRandomClinicId, Boolean modelCanBePublic) {
        if (globalUniqueExperimentId == null) {
            logger.warnf("ExperimentId is null");
            return;
        }
        logger.infof("Starting learning for experiment %s with clinic %s", globalUniqueExperimentId, uniqueRandomClinicId);
        StartLearningClientRequestDTO startLearningClientRequestDTO = new StartLearningClientRequestDTO(globalUniqueExperimentId, uniqueRandomClinicId, modelCanBePublic);
        FedDBClientDataDTO<StartLearningClientRequestDTO> fedDBClientDataDTO = new FedDBClientDataDTO<>(
                FedDBClientTypeEnum.START_LEARNING, startLearningClientRequestDTO);

        sendMessageToExperiment(fedDBClientDataDTO, globalUniqueExperimentId);
    }

    /**
     * Moves to the next step in the learning process for a federated experiment.
     * Sends a message to all clients participating in this experiment to proceed to the next step.
     *
     * @param globalUniqueExperimentId the id of the experiment to move to the next step
     * @param currentStepId            the next current step id
     * @param currentNodeId            the workflow node id that should become active
     */
    public void nextStepLearning(String globalUniqueExperimentId, Long currentStepId, String currentNodeId) {
        if (globalUniqueExperimentId == null) {
            logger.warnf("ExperimentId is null");
            return;
        }
        logger.infof("Moving to next currentNodeId %d (%s) for experiment %s", currentStepId, currentNodeId, globalUniqueExperimentId);
        LearningClientSyncRequestDTO startLearningClientRequestDTO = LearningClientSyncRequestDTO.createNextRequest(
                currentStepId.intValue(),
                currentNodeId,
                globalUniqueExperimentId
        );
        FedDBClientDataDTO<LearningClientSyncRequestDTO> fedDBClientDataDTO = new FedDBClientDataDTO<>(
                FedDBClientTypeEnum.UPDATE_LEARNING, startLearningClientRequestDTO);

        sendMessageToExperiment(fedDBClientDataDTO, globalUniqueExperimentId);
    }

    /**
     * Start the current step in the learning process for a federated experiment.
     * Sends a message to all clients participating in this experiment to proceed to the next step.
     *
     * @param globalUniqueExperimentId the id of the experiment to move to the next step
     * @param currentStepId            the next step number
     * @param currentNodeId            the workflow node id that should be started
     */
    public void startStepLearning(String globalUniqueExperimentId, Long currentStepId, String currentNodeId, Map<String, FederatedLearningRelayInfoDTO> relayData) {
        if (globalUniqueExperimentId == null) {
            logger.warnf("ExperimentId is null");
            return;
        }
        logger.infof("Start currentNodeId %d (%s) for experiment %s", currentStepId, currentNodeId, globalUniqueExperimentId);

        for (Map.Entry<String, FederatedLearningRelayInfoDTO> entry : relayData.entrySet()) {
            LearningClientSyncRequestDTO startLearningClientRequestDTO = LearningClientSyncRequestDTO.createRunRequest(
                    currentStepId.intValue(),
                    currentNodeId,
                    globalUniqueExperimentId
            );
            startLearningClientRequestDTO.setRelayInfo(entry.getValue());
            startLearningClientRequestDTO.setUniqueRandomClinicId(entry.getKey());
            FedDBClientDataDTO<LearningClientSyncRequestDTO> fedDBClientDataDTO = new FedDBClientDataDTO<>(
                    FedDBClientTypeEnum.UPDATE_LEARNING, startLearningClientRequestDTO);
            sendMessageToExperiment(fedDBClientDataDTO, globalUniqueExperimentId);
        }

    }


    /**
     * Stops the learning process for a federated experiment.
     * Sends a message to all clients participating in this experiment to halt learning.
     *
     * @param globalUniqueExperimentId the id of the experiment to stop learning
     */
    public void stopLearning(String globalUniqueExperimentId) {
        if (globalUniqueExperimentId == null) {
            logger.warnf("ExperimentId is null");
            return;
        }
        logger.infof("Stopping learning for experiment %s", globalUniqueExperimentId);
        LearningClientStopRequestDTO startLearningClientRequestDTO = LearningClientStopRequestDTO.createRequest(globalUniqueExperimentId);
        FedDBClientDataDTO<LearningClientStopRequestDTO> fedDBClientDataDTO = new FedDBClientDataDTO<>(
                FedDBClientTypeEnum.STOP_LEARNING, startLearningClientRequestDTO);

        sendMessageToExperiment(fedDBClientDataDTO, globalUniqueExperimentId);
    }

    /**
     * Sends a message to all clients participating in a specific experiment.
     * Filters connections based on the experiment ID and delivers the message only to relevant participants.
     *
     * @param message                  The message to be sent
     * @param globalUniqueExperimentId The identifier of the target experiment
     */
    private void sendMessageToExperiment(FedDBClientDataDTO<?> message, String globalUniqueExperimentId) {
        logger.debugf("Sending message to experiment %s: %s", globalUniqueExperimentId, message);
        connections.findByEndpointId(ENDPOINT_ID)
                .stream()
                .filter(c -> {
                    Set<String> experiments = learningConnectionMap.get(c.id());
                    if (experiments == null) {
                        logger.warnf("No experiment found for connection %s", c.id());
                        return false;
                    }
                    logger.infof("Connection %s is associated with experiments: %s", c.id(), experiments);
                    if (experiments.contains(globalUniqueExperimentId)) {
                        logger.infof("Connection %s is associated with experiment %s", c.id(), globalUniqueExperimentId);
                    } else {
                        logger.warnf("Connection %s is not associated with experiment %s", c.id(), globalUniqueExperimentId);
                    }
                    return experiments.contains(globalUniqueExperimentId);
                })
                .forEach(c -> {
                    try {
                        c.sendTextAndAwait(message);
                        observerEmitter.emit(FLNetClientObserverEventDTO.sent(
                                c.id(),
                                message.getMessageType() != null ? message.getMessageType().name() : "UNKNOWN",
                                message.getMessage()
                        ));
                    } catch (Exception e) {
                        logger.errorf("Failed to send message to connection %s: %s", c.id(), e.getMessage());
                    }
                });
    }

    /**
     * Broadcasts a message to all connected WebSocket clients.
     * Used for system-wide announcements and queries.
     *
     * @param message The message to be broadcast to all connections
     */
    private void sendMessageToAll(FedDBClientDataDTO<?> message) {
        logger.infof("Broadcasting message to all connections: %s", message);
        connections.findByEndpointId(ENDPOINT_ID)
                .forEach(c -> {
                    try {
                        c.sendTextAndAwait(message);
                        observerEmitter.emit(FLNetClientObserverEventDTO.sent(
                                c.id(),
                                message.getMessageType() != null ? message.getMessageType().name() : "UNKNOWN",
                                message.getMessage()
                        ));
                    } catch (Exception e) {
                        logger.errorf("Failed to broadcast message to connection %s: %s", c.id(), e.getMessage());
                    }
                });
    }


    /**
     * Retrieves a list of all active connection IDs.
     *
     * @return List of connection identifiers for all currently connected clients
     */
    public List<String> getConnections() {
        return connections.findByEndpointId(ENDPOINT_ID)
                .stream()
                .map(Connection::id)
                .toList();
    }

    /**
     * Closes all active WebSocket connections.
     * Each closed connection triggers the normal @OnClose lifecycle callback.
     */
    public void closeAllConnections() {
        connections.findByEndpointId(ENDPOINT_ID)
                .forEach(c -> {
                    try {
                        c.closeAndAwait();
                        logger.infof("Closed connection: %s", c.id());
                    } catch (Exception e) {
                        logger.errorf("Failed to close connection %s: %s", c.id(), e.getMessage());
                    }
                });
    }


}
