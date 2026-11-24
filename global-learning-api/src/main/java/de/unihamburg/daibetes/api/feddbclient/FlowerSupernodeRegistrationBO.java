package de.unihamburg.daibetes.api.feddbclient;

import bio.cosy.feddb.core.api.socket.FlowerSupernodeKeyRequestDTO;
import bio.cosy.feddb.core.execution.run.dto.AppMessageWrapperDTO;
import bio.cosy.feddb.core.execution.run.dto.FlowerSupernodeKeyDTO;
import bio.cosy.feddb.core.execution.run.dto.FlowerSupernodeRegisteredDTO;
import bio.cosy.feddb.core.execution.run.types.AppConnectionTypesEnum;
import bio.cosy.feddb.core.execution.run.types.AppMessageTypeEnum;
import bio.cosy.feddb.core.execution.run.types.AppRunTypeEnum;
import de.unihamburg.daibetes.api.project.training.federated.FederatedTrainingFlowerAggregatorBO;
import de.unihamburg.daibetes.execution.attempt.ExecutionAttemptWebsocketService;
import io.quarkus.arc.log.LoggerName;
import io.quarkus.websockets.next.OpenConnections;
import io.quarkus.websockets.next.WebSocketConnection;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lets a clinic's Flower SuperNode join the platform's SuperLink, which only admits registered SuperNodes once it
 * runs with TLS ({@code --enable-supernode-auth}). The clinic sends its SuperNode's public key; this checks the
 * clinic takes part in the step, pushes the key into the connection of the step's Flower aggregator attempt, whose
 * app registers it with its own SuperLink, and passes the app's answer back to the clinic by request id.
 * <p>
 * Nothing is persisted: the clinic's app re-sends its key until it is answered, so a refusal here (for instance the
 * aggregator not connected yet) only means "ask again". Same shape of checks as {@link RelayCertSignBO}.
 */
@ApplicationScoped
public class FlowerSupernodeRegistrationBO {

    // Same answer for every refusal, so a client cannot probe which experiments, clinics or steps exist
    static final String ERROR_REFUSED = "Registering the Flower SuperNode was refused";
    static final String ERROR_NOT_READY = "The Flower aggregator of this step is not connected yet";

    private static final String ATTEMPT_ENDPOINT_ID = ExecutionAttemptWebsocketService.class.getName();

    record Pending(String clinicConnectionId, Long aggregatorAttemptId) {
    }

    // request id -> who asked and which aggregator was asked, until the aggregator answers
    private final Map<String, Pending> pending = new ConcurrentHashMap<>();

    @Inject
    FLNetClientBroadcastBO broadcastBO;

    @Inject
    FederatedTrainingFlowerAggregatorBO flowerAggregatorBO;

    @Inject
    OpenConnections connections;

    @LoggerName("FlowerSupernodeRegistration")
    Logger logger;

    /** Forwards the clinic's key to the step's aggregator, or answers the clinic right away if that can't be done. */
    public void forward(final FlowerSupernodeKeyRequestDTO request, final String connectionId) {
        if (request == null || request.getRequestId() == null || request.getPublicKey() == null || request.getPublicKey().isBlank()) {
            logger.warnf("Refused Flower SuperNode registration without key from connection %s", connectionId);
            reply(connectionId, FlowerSupernodeRegisteredDTO.refused(request == null ? null : request.getRequestId(), ERROR_REFUSED));
            return;
        }
        if (!broadcastBO.isConnectionInExperiment(connectionId, request.getGlobalUniqueExperimentId())) {
            logger.warnf("Refused Flower SuperNode registration from connection %s: not registered for experiment %s",
                    connectionId, request.getGlobalUniqueExperimentId());
            reply(connectionId, FlowerSupernodeRegisteredDTO.refused(request.getRequestId(), ERROR_REFUSED));
            return;
        }
        Optional<Long> aggregatorAttemptId = flowerAggregatorBO.findAggregatorAttemptIdTransactional(
                request.getGlobalUniqueExperimentId(), request.getCurrentNodeId(), request.getUniqueRandomClinicId());
        if (aggregatorAttemptId.isEmpty()) {
            logger.warnf("Refused Flower SuperNode registration from connection %s: clinic %s has no Flower step %s in experiment %s",
                    connectionId, request.getUniqueRandomClinicId(), request.getCurrentNodeId(), request.getGlobalUniqueExperimentId());
            reply(connectionId, FlowerSupernodeRegisteredDTO.refused(request.getRequestId(), ERROR_REFUSED));
            return;
        }
        Optional<WebSocketConnection> aggregator = appConnectionOf(aggregatorAttemptId.get());
        if (aggregator.isEmpty()) {
            reply(connectionId, FlowerSupernodeRegisteredDTO.refused(request.getRequestId(), ERROR_NOT_READY));
            return;
        }
        pending.put(request.getRequestId(), new Pending(connectionId, aggregatorAttemptId.get()));
        FlowerSupernodeKeyDTO key = new FlowerSupernodeKeyDTO(request.getRequestId(), request.getPublicKey(), request.getUniqueRandomClinicId());
        try {
            aggregator.get().sendTextAndAwait(new AppMessageWrapperDTO<>(AppMessageTypeEnum.FLOWER_SUPERNODE_KEY, key, AppRunTypeEnum.FEDERATED_RUN));
            logger.infof("Asked Flower aggregator attempt %d to register the SuperNode of clinic %s",
                    aggregatorAttemptId.get(), request.getUniqueRandomClinicId());
        } catch (RuntimeException e) {
            pending.remove(request.getRequestId());
            logger.warnf("Could not reach Flower aggregator attempt %d: %s", aggregatorAttemptId.get(), e.getMessage());
            reply(connectionId, FlowerSupernodeRegisteredDTO.refused(request.getRequestId(), ERROR_NOT_READY));
        }
    }

    /** Passes the aggregator's answer back to the clinic that asked - only from the aggregator that was asked. */
    public void answer(final Long aggregatorAttemptId, final FlowerSupernodeRegisteredDTO registered) {
        if (registered == null || registered.getRequestId() == null) {
            return;
        }
        Pending asked = pending.get(registered.getRequestId());
        if (asked == null || !Objects.equals(asked.aggregatorAttemptId(), aggregatorAttemptId)) {
            logger.warnf("Ignoring a Flower SuperNode registration answer of attempt %d nobody is waiting for", aggregatorAttemptId);
            return;
        }
        pending.remove(registered.getRequestId());
        reply(asked.clinicConnectionId(), registered);
    }

    private void reply(String connectionId, FlowerSupernodeRegisteredDTO registered) {
        broadcastBO.sendFlowerSupernodeRegistered(connectionId, registered);
    }

    private Optional<WebSocketConnection> appConnectionOf(Long attemptId) {
        return connections.findByEndpointId(ATTEMPT_ENDPOINT_ID).stream()
                .filter(connection -> !connection.isClosed())
                .filter(connection -> Objects.equals(String.valueOf(attemptId), connection.pathParam("runId")))
                .filter(connection -> AppConnectionTypesEnum.APP.isEqual(connection.pathParam("type")))
                .findFirst();
    }
}
