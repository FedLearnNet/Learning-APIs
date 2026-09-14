package de.unihamburg.daibetes.api.query;


import bio.cosy.feddb.core.api.datamodler.datatype.DataTypeNodeDTO;
import bio.cosy.feddb.core.api.datamodler.ontology.OntologyNodeDTO;
import bio.cosy.feddb.core.api.query.EnhancedQueryItemDTO;
import bio.cosy.feddb.core.api.query.QueryDTO;
import bio.cosy.feddb.core.api.query.QueryDetailDTO;
import bio.cosy.feddb.core.api.query.QueryItemDTO;
import bio.cosy.feddb.core.api.socket.ProjectFederatedRequestDataStatisticsDTO;
import bio.cosy.feddb.core.base.BaseBo;
import de.unihamburg.daibetes.api.feddbclient.FLNetClientBroadcastBO;
import de.unihamburg.daibetes.services.DataModelerDataTypeService;
import de.unihamburg.daibetes.services.DataModelerOntologyService;
import io.quarkus.arc.log.LoggerName;
import io.quarkus.logging.Log;
import io.smallrye.reactive.messaging.MutinyEmitter;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotAllowedException;
import jakarta.ws.rs.NotFoundException;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.OnOverflow;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

import java.util.*;

@ApplicationScoped
public class QueryBO extends BaseBo<QueryDTO, QueryEntity, QueryAO, QueryMapper> {

    @Inject
    FLNetClientBroadcastBO broadcastBO;

    @Channel("queries")
    @OnOverflow(OnOverflow.Strategy.DROP)
    MutinyEmitter<QueryDTO> emitter;

    @LoggerName("QueryBO")
    Logger logger;

    @Inject
    @RestClient
    DataModelerOntologyService dataModelerOntologyService;

    @Inject
    @RestClient
    DataModelerDataTypeService dataModelerDataTypeService;

    public QueryDTO getById(Long id, String keycloakId) {
        Optional<QueryEntity> entity = ao.findByIdForUser(id, keycloakId);
        if (entity.isEmpty()) {
            throw new NotFoundException(String.format("Query with the ID %s not found", id));
        }
        return mapper.entityToDto(entity.get());
    }

    public QueryDetailDTO getDetailById(Long id, String keycloakId) {
        Optional<QueryEntity> entity = ao.findByIdForUser(id, keycloakId);
        if (entity.isEmpty()) {
            throw new NotFoundException(String.format("Query with the ID %s not found", id));
        }

        QueryEntity currentEntity = entity.get();
        QueryDetailDTO detailed = mapper.entityToDetailDto(currentEntity);
        List<QueryEntity> versions = ao.findAllVersionsByGroupIdForUser(resolveGroupId(currentEntity), keycloakId);
        int currentIndex = 0;
        for (int i = 0; i < versions.size(); i++) {
            if (versions.get(i).getId().equals(currentEntity.getId())) {
                currentIndex = i;
                break;
            }
        }
        detailed.setOlderQueries(versions.stream()
                .skip(currentIndex + 1L)
                .map(mapper::entityToDto)
                .toList());

        List<UUID> ontologyIds = detailed.getQuery().stream()
                .map(QueryItemDTO::getOntologyId)
                .filter(Objects::nonNull)
                .map(this::tryParseUuid)
                .flatMap(Optional::stream)
                .toList();

        List<UUID> dataTypeIds = detailed.getQuery().stream()
                .map(QueryItemDTO::getDataTypeId)
                .filter(Objects::nonNull)
                .map(this::tryParseUuid)
                .flatMap(Optional::stream)
                .toList();

        if (!ontologyIds.isEmpty()) {
            List<OntologyNodeDTO> ontologyNodes = Collections.emptyList();
            List<DataTypeNodeDTO> dataTypeNodes = Collections.emptyList();
            try {
                ontologyNodes = dataModelerOntologyService.getByIds(ontologyIds)
                        .await().indefinitely();
            } catch (Exception e) {
                Log.errorf(e, "Failed to fetch ontology nodes for query with ID %s", id);
            }
            try {
                dataTypeNodes = dataModelerDataTypeService.getByIds(dataTypeIds)
                        .await().indefinitely();
            } catch (Exception e) {
                Log.errorf(e, "Failed to fetch data type nodes for query with ID %s", id);
            }
            List<EnhancedQueryItemDTO> enhancedQueryItems = new ArrayList<>();
            for (QueryItemDTO item : detailed.getQuery()) {
                EnhancedQueryItemDTO enhanced = mapper.queryToEnhanced(item);
                ontologyNodes.stream()
                        .filter(n -> n.getId().toString().equals(item.getOntologyId()))
                        .findFirst().ifPresent(node -> enhanced.setOntologyName(
                                node.getNames().stream()
                                        .findFirst()
                                        .orElse(null)
                        ));
                dataTypeNodes.stream()
                        .filter(n -> n.getId().toString().equals(item.getOntologyId()))
                        .findFirst().ifPresent(node -> enhanced.setDataTypeName(node.getName()));
                enhancedQueryItems.add(enhanced);
            }
            detailed.setEnhancedQuery(enhancedQueryItems);

        }

        return detailed;
    }

    private Optional<UUID> tryParseUuid(String value) {
        try {
            return Optional.of(UUID.fromString(value));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public List<QueryDTO> getAll(String keycloakId) {
        Map<String, QueryEntity> latestByGroup = new LinkedHashMap<>();
        for (QueryEntity entity : ao.getAllByUser(keycloakId)) {
            latestByGroup.putIfAbsent(resolveGroupId(entity), entity);
        }
        return latestByGroup.values().stream()
                .map(mapper::entityToDto)
                .toList();
    }

    public QueryDTO create(QueryCreateDTO request, String keycloakId) {
        return create(request, keycloakId, null);
    }

    public QueryDTO create(QueryCreateDTO request, String keycloakId, String groupId) {
        if (groupId == null) {
            groupId = UUID.randomUUID().toString();
        }
        QueryEntity entity = mapper.createDtoToEntity(request);
        entity.setKeycloakId(keycloakId);
        entity.setGroupId(groupId);
        entity.setGlobalUniqueId(UUID.randomUUID().toString());
        ao.persist(entity);
        QueryDTO dto = mapper.entityToDto(entity);
        emitQueryUpdate(dto);
        return dto;
    }

    public QueryDTO createAndRun(QueryCreateDTO request, String keycloakId, Set<String> roles) {
        String groupId = request.getGroupId();
        QueryDTO dto = create(request, keycloakId, groupId);
        return fireQuery(dto, roles);
    }

    public void setError(String globalUniqueId, String error) {
        long updatedRows = ao.updateErrorByGlobalUniqueId(globalUniqueId, error);
        if (updatedRows == 0) {
            throw new NotFoundException("Query not found");
        }
        try {
            QueryEntity entity = ao.findByGlobalUniqueID(globalUniqueId)
                    .orElseThrow(() -> new NotFoundException("Query not found"));
            emitQueryUpdate(mapper.entityToDto(entity));
        } catch (Exception e) {
            Log.errorf(e, "Failed to send error update for query with globalUniqueId %s", globalUniqueId);
        }
    }

    public void setCount(String globalUniqueId, int count) {
        long updatedRows = ao.updateCountByGlobalUniqueId(globalUniqueId, count);
        if (updatedRows == 0) {
            throw new NotFoundException("Query not found");
        }

        try {
            QueryEntity entity = ao.findByGlobalUniqueID(globalUniqueId)
                    .orElseThrow(() -> new NotFoundException("Query not found"));
            emitQueryUpdate(mapper.entityToDto(entity));
        } catch (Exception e) {
            Log.errorf(e, "Failed to send update for query with globalUniqueId %s", globalUniqueId);
        }
    }

    public QueryDTO update(Long id, QueryDTO request, String keycloakId) {
        QueryEntity source = ao.findByIdForUser(id, keycloakId)
                .orElseThrow(() -> new NotFoundException(String.format("Query with the ID %s not found", id)));

        if (request.getId() != null && !request.getId().equals(id)) {
            throw new NotAllowedException(String.format("ID %s not matching with the object", id));
        }

        QueryEntity entity = mapper.createVersionFromRequest(source, request);
        entity.setGroupId(resolveGroupId(source));
        entity.setGlobalUniqueId(UUID.randomUUID().toString());
        ao.persist(entity);
        QueryDTO dto = mapper.entityToDto(entity);
        emitQueryUpdate(dto);
        return dto;
    }


    public void deleteById(Long pk, String keycloakId) {
        QueryDTO query = getById(pk);
        if (query == null) {
            throw new NotFoundException("Query not found");
        }

        if (query.isHasFired()) {
            throw new NotAllowedException("You cannot delete a query that has already been fired");
        }

        if (!keycloakId.equals(query.getKeycloakId())) {
            throw new NotAllowedException("You are not the owner of this query");
        }

        deleteById(pk);
    }

    public List<String> getConnections() {
        return broadcastBO.getConnections();
    }

    public QueryDTO fireQuery(Long queryId, String keycloakId, Set<String> roles) {
        QueryEntity source = ao.findByIdForUser(queryId, keycloakId)
                .orElseThrow(() -> new NotFoundException(String.format("Query with the ID %s not found", queryId)));
        QueryDTO query = mapper.entityToDto(source);
        if (source.isHasFired()) {
            QueryEntity rerun = mapper.createVersionFromEntity(source);
            rerun.setGroupId(resolveGroupId(source));
            rerun.setGlobalUniqueId(UUID.randomUUID().toString());
            ao.persist(rerun);
            query = mapper.entityToDto(rerun);
            emitQueryUpdate(query);
        }
        return fireQuery(query, roles);
    }

    public QueryDTO fireDataStatistics(Long queryId, String keycloakId) {
        Optional<QueryEntity> entityOptional = ao.findByIdForUser(queryId, keycloakId);
        if (entityOptional.isEmpty()) {
            throw new NotFoundException(String.format("Query with the ID %s not found", queryId));
        }
        QueryEntity query = entityOptional.get();
        query.setLatestDataStatisticsRequest(UUID.randomUUID());
        query.setLatestDataStatisticsRequestTimestamp(new Date());
        ao.persist(query);
        QueryDTO queryDTO = mapper.entityToDto(query);
        emitQueryUpdate(queryDTO);
        ProjectFederatedRequestDataStatisticsDTO request = new ProjectFederatedRequestDataStatisticsDTO();
        request.setGlobalRequestId(query.getLatestDataStatisticsRequest());
        request.setGlobalUniqueQueryId(query.getGlobalUniqueId());
        request.setKeycloakId(keycloakId);
        broadcastBO.fireDataStatisticsQuery(request);
        return queryDTO;
    }

    public QueryDTO fireQuery(QueryDTO query, Set<String> roles) {
        query.setHasFired(true);
        query = update(query);
        query.setRoles(roles);
        emitQueryUpdate(query);
        broadcastBO.fireExistingQuery(query);
        return query;
    }

    public boolean isLatestVersion(Long id, String keycloakId) {
        Optional<QueryEntity> entityOptional = ao.findByIdForUser(id, keycloakId);
        if (entityOptional.isEmpty()) {
            return false;
        }
        QueryEntity entity = entityOptional.get();
        return ao.findLatestVersionByGroupIdForUser(resolveGroupId(entity), keycloakId)
                .map(latest -> latest.getId().equals(id))
                .orElse(false);
    }

    private String resolveGroupId(QueryEntity entity) {
        if (entity.getGroupId() != null && !entity.getGroupId().isBlank()) {
            return entity.getGroupId();
        }
        if (entity.getGlobalUniqueId() != null && !entity.getGlobalUniqueId().isBlank()) {
            return entity.getGlobalUniqueId();
        }
        return String.valueOf(entity.getId());
    }

    private void emitQueryUpdate(QueryDTO dto) {
        if (!emitter.hasRequests()) {
            logger.warn("No subscribers available for update query. Message not sent for query " + dto.getGlobalUniqueId());
            return;
        }
        emitter.sendAndForget(dto);
    }

}
