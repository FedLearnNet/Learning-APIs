package de.unihamburg.daibetes.api.query;

import bio.cosy.feddb.core.api.datamodler.datatype.DataTypeNodeDTO;
import bio.cosy.feddb.core.api.datamodler.ontology.OntologyNodeDTO;
import bio.cosy.feddb.core.api.query.QueryDTO;
import bio.cosy.feddb.core.api.query.QueryDetailDTO;
import bio.cosy.feddb.core.api.query.QueryRejectedException;
import bio.cosy.feddb.core.api.query.QueryValidationBO;
import bio.cosy.feddb.core.api.query.item.EnhancedQueryItemDTO;
import bio.cosy.feddb.core.api.query.item.QueryItemDTO;
import bio.cosy.feddb.core.api.query.item.QueryItemHelper;
import bio.cosy.feddb.core.api.socket.ProjectFederatedRequestDataStatisticsDTO;
import bio.cosy.feddb.core.base.BaseBo;
import de.unihamburg.daibetes.api.feddbclient.FLNetClientBroadcastBO;
import de.unihamburg.daibetes.api.query.response.QueryResponseBO;
import de.unihamburg.daibetes.services.DataModelerDataTypeService;
import de.unihamburg.daibetes.services.DataModelerOntologyService;
import io.quarkus.logging.Log;
import io.smallrye.reactive.messaging.MutinyEmitter;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotAllowedException;
import jakarta.ws.rs.NotFoundException;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.OnOverflow;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.util.*;
import java.util.function.Function;

/**
 * Saves, versions and fires the queries of a user; every change is pushed to the user's query stream.
 */
@ApplicationScoped
public class QueryBO extends BaseBo<QueryDTO, QueryEntity, QueryAO, QueryMapper> {

    @Inject
    FLNetClientBroadcastBO broadcastBO;

    @Inject
    QueryValidationBO validationBO;

    @Inject
    QueryResponseBO responseBO;

    @Channel("queries")
    @OnOverflow(OnOverflow.Strategy.DROP)
    MutinyEmitter<QueryDTO> emitter;

    @Inject
    @RestClient
    DataModelerOntologyService dataModelerOntologyService;

    @Inject
    @RestClient
    DataModelerDataTypeService dataModelerDataTypeService;

    /**
     * Query of the user, NotFoundException otherwise.
     */
    public QueryDTO getById(Long id, String keycloakId) {
        return mapper.entityToDto(findOwned(id, keycloakId));
    }

    /**
     * Query of the user with its older versions and the names of ontologies and data types.
     */
    public QueryDetailDTO getDetailById(Long id, String keycloakId) {
        QueryEntity entity = findOwned(id, keycloakId);
        QueryDetailDTO detailed = mapper.entityToDetailDto(entity);
        detailed.setOlderQueries(olderVersions(entity, keycloakId));
        List<QueryItemDTO> leaves = QueryItemHelper.leavesOf(detailed.getQuery());
        if (!leaves.isEmpty()) {
            detailed.setEnhancedQuery(namedLeaves(leaves, id));
        }
        return detailed;
    }

    /**
     * Latest version of each query of the user, newest first.
     */
    public List<QueryDTO> getAll(String keycloakId) {
        Map<String, QueryEntity> latestByGroup = new LinkedHashMap<>();
        for (QueryEntity entity : ao.listByUser(keycloakId)) {
            latestByGroup.putIfAbsent(resolveGroupId(entity), entity);
        }
        return latestByGroup.values().stream().map(mapper::entityToDto).toList();
    }

    /**
     * Saves a new query, as new version when the request names a group.
     */
    public QueryDTO create(QueryCreateDTO request, String keycloakId) {
        validateQuery(request);
        QueryEntity entity = mapper.createDtoToEntity(request);
        entity.setKeycloakId(keycloakId);
        if (StringUtils.isBlank(request.getGroupId())) {
            entity.setGroupId(UUID.randomUUID().toString());
        } else {
            entity.setGroupId(request.getGroupId());
        }
        entity.setGlobalUniqueId(UUID.randomUUID().toString());
        ao.persist(entity);
        return emitted(entity);
    }

    /**
     * Saves a new query and fires it right away.
     */
    public QueryDTO createAndRun(QueryCreateDTO request, String keycloakId, Set<String> roles) {
        return fire(create(request, keycloakId), roles);
    }

    /**
     * Saves the request as new version of the user's query; the old version stays.
     */
    public QueryDTO update(Long id, QueryDTO request, String keycloakId) {
        QueryEntity source = findOwned(id, keycloakId);
        if (request.getId() != null && !request.getId().equals(id)) {
            throw new NotAllowedException(String.format("ID %s not matching with the object", id));
        }
        validateQuery(request);
        return emitted(persistVersion(mapper.createVersionFromRequest(source, request), source));
    }

    /**
     * Deletes a query of the user that was never fired.
     */
    public void deleteById(Long id, String keycloakId) {
        if (findOwned(id, keycloakId).isHasFired()) {
            throw new NotAllowedException("You cannot delete a query that has already been fired");
        }
        deleteById(id);
    }

    /**
     * Sends the user's query to all clients; a fired query runs again as new version.
     */
    public QueryDTO fireQuery(Long id, String keycloakId, Set<String> roles) {
        QueryEntity source = findOwned(id, keycloakId);
        if (source.isHasFired()) {
            return fire(emitted(persistVersion(mapper.createVersionFromEntity(source), source)), roles);
        }
        return fire(mapper.entityToDto(source), roles);
    }

    /**
     * Asks all clients for statistics of the matched data; only after the result is released.
     */
    public QueryDTO fireDataStatistics(Long id, String keycloakId) {
        QueryEntity query = findOwned(id, keycloakId);
        if (!query.isHasResult()) {
            throw new BadRequestException("The query result is not released yet");
        }
        query.setLatestDataStatisticsRequest(UUID.randomUUID());
        query.setLatestDataStatisticsRequestTimestamp(new Date());
        QueryDTO dto = emitted(query);

        ProjectFederatedRequestDataStatisticsDTO request = new ProjectFederatedRequestDataStatisticsDTO();
        request.setGlobalRequestId(query.getLatestDataStatisticsRequest());
        request.setGlobalUniqueQueryId(query.getGlobalUniqueId());
        request.setKeycloakId(keycloakId);
        broadcastBO.fireDataStatisticsQuery(request);
        return dto;
    }

    /**
     * Ids of the connected clients.
     */
    public List<String> getConnections() {
        return broadcastBO.getConnections();
    }

    /**
     * True when no newer version of the user's query exists.
     */
    public boolean isLatestVersion(Long id, String keycloakId) {
        Optional<QueryEntity> entity = ao.findByIdForUser(id, keycloakId);
        if (entity.isEmpty()) {
            return false;
        }
        return ao.findLatestVersionForUser(resolveGroupId(entity.get()), keycloakId)
                .map(latest -> latest.getId().equals(id))
                .orElse(false);
    }

    /**
     * Stores the released result and pushes it to the user.
     */
    public void releaseResult(QueryEntity entity, int result) {
        ao.updateResult(entity, result);
        emitted(entity);
    }

    private QueryEntity findOwned(Long id, String keycloakId) {
        return ao.findByIdForUser(id, keycloakId)
                .orElseThrow(() -> new NotFoundException(String.format("Query with the ID %s not found", id)));
    }

    private void validateQuery(QueryDTO request) {
        if (validationBO.isEmptyQuery(request.getQuery())) {
            return;
        }
        try {
            validationBO.validateTree(request.getQuery());
        } catch (QueryRejectedException e) {
            throw new BadRequestException(e.getMessage());
        }
    }

    // every connected client has to answer; the deadline releases what came until then
    private QueryDTO fire(QueryDTO query, Set<String> roles) {
        query.setHasFired(true);
        QueryDTO fired = update(query);
        fired.setRoles(roles);
        ao.findById(fired.getId()).setExpectedAnswers(broadcastBO.getConnections().size());
        // the written row stays locked until commit, so early answers wait for it
        ao.flush();
        emitQueryUpdate(fired);
        broadcastBO.fireExistingQuery(fired);
        responseBO.releaseAtDeadline(fired.getGlobalUniqueId());
        return fired;
    }

    // new version in the group of the source, with its own id for the clients
    private QueryEntity persistVersion(QueryEntity version, QueryEntity source) {
        version.setGroupId(resolveGroupId(source));
        version.setGlobalUniqueId(UUID.randomUUID().toString());
        ao.persist(version);
        return version;
    }

    // versions are sorted newest first
    private List<QueryDTO> olderVersions(QueryEntity entity, String keycloakId) {
        List<QueryEntity> versions = ao.listVersionsForUser(resolveGroupId(entity), keycloakId);
        List<QueryDTO> older = new ArrayList<>();
        boolean isOlder = false;
        for (QueryEntity version : versions) {
            if (isOlder) {
                older.add(mapper.entityToDto(version));
            }
            if (version.getId().equals(entity.getId())) {
                isOlder = true;
            }
        }
        return older;
    }

    private List<EnhancedQueryItemDTO> namedLeaves(List<QueryItemDTO> leaves, Long queryId) {
        Map<String, String> ontologyNames = new HashMap<>();
        Map<String, String> dataTypeNames = new HashMap<>();
        // names stay empty when the data modeler is down
        try {
            for (OntologyNodeDTO node : dataModelerOntologyService.getByIds(uuidsOf(leaves, QueryItemDTO::getOntologyId)).await().indefinitely()) {
                ontologyNames.put(node.getId().toString(), node.getNames().stream().findFirst().orElse(null));
            }
            for (DataTypeNodeDTO node : dataModelerDataTypeService.getByIds(uuidsOf(leaves, QueryItemDTO::getDataTypeId)).await().indefinitely()) {
                dataTypeNames.put(node.getId().toString(), node.getName());
            }
        } catch (Exception e) {
            Log.errorf(e, "Failed to fetch ontology or data type names for query with ID %s", queryId);
        }
        List<EnhancedQueryItemDTO> named = new ArrayList<>();
        for (QueryItemDTO leaf : leaves) {
            EnhancedQueryItemDTO enhanced = mapper.queryToEnhanced(leaf);
            enhanced.setOntologyName(ontologyNames.get(leaf.getOntologyId()));
            enhanced.setDataTypeName(dataTypeNames.get(leaf.getDataTypeId()));
            named.add(enhanced);
        }
        return named;
    }

    // ids that are no UUID are skipped
    private List<UUID> uuidsOf(List<QueryItemDTO> leaves, Function<QueryItemDTO, String> id) {
        List<UUID> uuids = new ArrayList<>();
        for (QueryItemDTO leaf : leaves) {
            String value = id.apply(leaf);
            try {
                if (value != null) {
                    uuids.add(UUID.fromString(value));
                }
            } catch (IllegalArgumentException e) {
                Log.debugf("Skipped id %s, it is no UUID", value);
            }
        }
        return uuids;
    }

    // queries saved before groups existed fall back to their own ids
    private String resolveGroupId(QueryEntity entity) {
        if (StringUtils.isNotBlank(entity.getGroupId())) {
            return entity.getGroupId();
        }
        if (StringUtils.isNotBlank(entity.getGlobalUniqueId())) {
            return entity.getGlobalUniqueId();
        }
        return String.valueOf(entity.getId());
    }

    private QueryDTO emitted(QueryEntity entity) {
        QueryDTO dto = mapper.entityToDto(entity);
        emitQueryUpdate(dto);
        return dto;
    }

    private void emitQueryUpdate(QueryDTO dto) {
        if (!emitter.hasRequests()) {
            Log.warnf("No subscribers available for update query. Message not sent for query %s", dto.getGlobalUniqueId());
            return;
        }
        emitter.sendAndForget(dto);
    }
}
