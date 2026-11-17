package de.unihamburg.daibetes.api.query.response;

import bio.cosy.feddb.core.api.socket.FedDBClientResponseType;
import bio.cosy.feddb.core.api.query.QueryClientResponseDTO;
import bio.cosy.feddb.core.base.BaseBo;
import bio.cosy.feddb.core.execution.ExecutionMode;
import de.unihamburg.daibetes.api.query.QueryBO;
import io.quarkus.arc.log.LoggerName;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotFoundException;
import org.jboss.logging.Logger;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class QueryResponseBO extends BaseBo<QueryResponseDTO, QueryResponseEntity, QueryResponseAO, QueryResponseMapper> {

    // Keycloak name of each connected client, shown only when it shares it
    private final Map<String, String> clientNames = new ConcurrentHashMap<>();

    public void putClientName(String connectionId, String name) {
        if (name != null) {
            clientNames.put(connectionId, name);
        }
    }

    public void removeClientName(String connectionId) {
        clientNames.remove(connectionId);
    }


    @Inject
    QueryBO queryBO;

    @LoggerName("QueryClientResponse")
    Logger logger;

    public List<QueryResponseDTO> getAll(String keycloakId) {
        return mapper.entitiesToDtos(ao.getAllByUser(keycloakId).stream());
    }

    public List<QueryResponseDTO> getSystems(Long queryId, String keycloakId) {
        queryBO.getById(queryId, keycloakId);
        List<QueryResponseDTO> systems = mapper.entitiesToDtos(ao.list("query.id = ?1 and count > 0 and error is null", queryId).stream());
        // count and hardware of a single client only show when it allows TRE
        systems.stream().filter(s -> s.getSupportedModes() == null || !s.getSupportedModes().contains(ExecutionMode.TRE))
                .forEach(s -> {
                    s.setCount(null);
                    s.setSystem(null);
                    s.setClientName(null);
                });
        return systems;
    }

    public String findConnectionId(Long responseId, Long queryId) {
        return ao.findByIdOptional(responseId).filter(response -> response.getQuery().getId().equals(queryId))
                .map(QueryResponseEntity::getConnectionId)
                .orElseThrow(() -> new NotFoundException("Client " + responseId + " did not answer query " + queryId));
    }

    public void saveResponse(QueryClientResponseDTO request, String connectionId) {
        QueryResponseDTO dto = mapper.createDtoToDto(request);
        boolean isError = request.getType().equals(FedDBClientResponseType.ERROR);
        if (isError) {
            dto.setCount(0);
        }
        try {
            if (!isError && dto.getCount() != null && dto.getCount() > 0) {
                QueryResponseEntity response = mapper.dtoToEntity(dto);
                response.setQuery(queryBO.findEntityByGlobalUniqueId(request.getGlobalUniqueQueryId()));
                response.setConnectionId(connectionId);
                if (Boolean.TRUE.equals(request.getShareName())) {
                    response.setClientName(clientNames.get(connectionId));
                }
                ao.persist(response);
            }
            queryBO.setCount(request.getGlobalUniqueQueryId(), dto.getCount());
            if (isError && dto.getError() != null) {
                queryBO.setError(request.getGlobalUniqueQueryId(), dto.getError());
            }
        } catch (NotFoundException e) {
            logger.error("Query with global id " + request.getGlobalUniqueQueryId() + " not found for client ");
        }
    }


}
