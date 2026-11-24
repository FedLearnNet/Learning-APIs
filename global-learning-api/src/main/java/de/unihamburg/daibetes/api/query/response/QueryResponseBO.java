package de.unihamburg.daibetes.api.query.response;

import bio.cosy.feddb.core.api.query.QueryClientResponseDTO;
import bio.cosy.feddb.core.api.query.QueryDTO;
import bio.cosy.feddb.core.api.socket.FedDBClientResponseType;
import bio.cosy.feddb.core.base.BaseBo;
import bio.cosy.feddb.core.execution.ExecutionMode;
import de.unihamburg.daibetes.api.query.QueryAO;
import de.unihamburg.daibetes.api.query.QueryBO;
import de.unihamburg.daibetes.api.query.QueryEntity;
import de.unihamburg.daibetes.config.FLNetConfig;
import io.quarkus.logging.Log;
import io.quarkus.virtual.threads.VirtualThreads;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;


@ApplicationScoped
public class QueryResponseBO extends BaseBo<QueryResponseDTO, QueryResponseEntity, QueryResponseAO, QueryResponseMapper> {

    @Inject
    QueryBO queryBO;

    @Inject
    QueryAO queryAO;

    @Inject
    FLNetConfig config;

    @Inject
    @VirtualThreads
    ExecutorService virtualThreads;

    /**
     * Clients with matches of a released query; count, hardware and name only of TRE clients.
     */
    public List<QueryResponseDTO> getSystems(Long queryId, String keycloakId) {
        QueryDTO query = queryBO.getById(queryId, keycloakId);
        List<QueryResponseEntity> matched = ao.listMatchedByQuery(queryId);
        if (!query.isHasResult() || !hasEnoughMatches(matched)) {
            return List.of();
        }
        List<QueryResponseDTO> systems = mapper.entitiesToDtos(matched.stream());
        for (QueryResponseDTO system : systems) {
            if (!isTre(system.getSupportedModes())) {
                system.setCount(null);
                system.setSystem(null);
                system.setClientName(null);
            }
        }
        return systems;
    }

    /**
     * Connection of a client with matches of a released query, NotFoundException otherwise.
     */
    public String findConnectionId(Long responseId, Long queryId) {
        List<QueryResponseEntity> matched = ao.listMatchedByQuery(queryId);
        if (hasEnoughMatches(matched)) {
            for (QueryResponseEntity response : matched) {
                if (response.getId().equals(responseId) && response.getQuery().isHasResult()) {
                    return response.getConnectionId();
                }
            }
        }
        throw new NotFoundException("Client " + responseId + " did not answer query " + queryId);
    }

    /**
     * Releases the query at the deadline with the answers that came until then.
     */
    public void releaseAtDeadline(String globalUniqueId) {
        Uni.createFrom().item(globalUniqueId)
                .onItem().delayIt().by(config.query().releaseTimeout())
                .emitOn(virtualThreads)
                .invoke(this::releaseTransactional)
                .subscribe().with(released -> {
                }, e -> Log.errorf(e, "Failed to release query %s", globalUniqueId));
    }

    /**
     * Stores one answer per client silently; the last expected answer releases the result, later ones are ignored.
     * The client name comes from its login and is kept only when the client shares it.
     */
    public void saveResponse(QueryClientResponseDTO request, String connectionId, String clientName) {
        Optional<QueryEntity> found = queryAO.findForUpdateByGlobalUniqueId(request.getGlobalUniqueQueryId());
        if (found.isEmpty() || !found.get().isHasFired() || found.get().isHasResult()
                || ao.existsByQueryAndConnection(found.get().getId(), connectionId)) {
            Log.warnf("Ignored answer of client %s to query %s", connectionId, request.getGlobalUniqueQueryId());
            return;
        }
        QueryEntity query = found.get();
        QueryResponseEntity response = mapper.clientToEntity(request);
        response.setQuery(query);
        response.setConnectionId(connectionId);
        response.setCount(matchesOf(request));
        if (Boolean.TRUE.equals(request.getShareName())) {
            response.setClientName(clientName);
        }
        ao.persist(response);
        if (ao.countByQuery(query.getId()) >= query.getExpectedAnswers()) {
            release(query);
        }
    }

    /**
     * Releases the query unless all clients answered before.
     */
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void releaseTransactional(String globalUniqueId) {
        Optional<QueryEntity> query = queryAO.findForUpdateByGlobalUniqueId(globalUniqueId);
        if (query.isPresent() && !query.get().isHasResult()) {
            release(query.get());
        }
    }

    private void release(QueryEntity query) {
        queryBO.releaseResult(query, releasedCount(ao.listMatchedByQuery(query.getId())));
    }

    // a rejection counts as an answer without matches
    private int matchesOf(QueryClientResponseDTO request) {
        if (FedDBClientResponseType.ERROR.equals(request.getType()) || request.getCount() == null) {
            return 0;
        }
        return Math.clamp(request.getCount(), 0, Integer.MAX_VALUE);
    }

    // sum held back as 0 until enough clients, or one TRE client, matched
    private int releasedCount(List<QueryResponseEntity> matched) {
        if (!hasEnoughMatches(matched)) {
            return 0;
        }
        return matched.stream().mapToInt(QueryResponseEntity::getCount).sum();
    }

    private boolean hasEnoughMatches(List<QueryResponseEntity> matched) {
        return matched.size() >= config.query().minQueryResults()
                || matched.stream().anyMatch(response -> isTre(response.getSupportedModes()));
    }

    private boolean isTre(Set<ExecutionMode> supportedModes) {
        return supportedModes != null && supportedModes.contains(ExecutionMode.TRE);
    }
}
