package de.unihamburg.daibetes.api.query.response;

import bio.cosy.feddb.core.api.socket.FedDBClientResponseType;
import bio.cosy.feddb.core.api.query.QueryClientResponseDTO;
import bio.cosy.feddb.core.base.BaseBo;
import de.unihamburg.daibetes.api.query.QueryBO;
import io.quarkus.arc.log.LoggerName;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotFoundException;
import org.jboss.logging.Logger;

import java.util.List;

@ApplicationScoped
public class QueryResponseBO extends BaseBo<QueryResponseDTO, QueryResponseEntity, QueryResponseAO, QueryResponseMapper> {


    @Inject
    QueryBO queryBO;

    @LoggerName("QueryClientResponse")
    Logger logger;

    public List<QueryResponseDTO> getAll(String keycloakId) {
        return mapper.entitiesToDtos(ao.getAllByUser(keycloakId).stream());
    }

    public void saveResponse(QueryClientResponseDTO request) {
        QueryResponseDTO dto = mapper.createDtoToDto(request);
        boolean isError = request.getType().equals(FedDBClientResponseType.ERROR);
        if (isError) {
            dto.setCount(0);
        }
        try {
            queryBO.setCount(request.getGlobalUniqueQueryId(), dto.getCount());
            if (isError && dto.getError() != null) {
                queryBO.setError(request.getGlobalUniqueQueryId(), dto.getError());
            }
        } catch (NotFoundException e) {
            logger.error("Query with global id " + request.getGlobalUniqueQueryId() + " not found for client ");
        }
    }


}
