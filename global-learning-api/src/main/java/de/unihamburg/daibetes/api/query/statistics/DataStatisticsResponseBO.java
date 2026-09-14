package de.unihamburg.daibetes.api.query.statistics;

import bio.cosy.feddb.core.api.socket.DataStatisticsResponseClientDTO;
import bio.cosy.feddb.core.base.BaseBo;
import de.unihamburg.daibetes.api.query.QueryAO;
import de.unihamburg.daibetes.api.query.QueryEntity;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class DataStatisticsResponseBO extends BaseBo<DataStatisticsResponseDTO, DataStatisticsResponseEntity, DataStatisticsResponseAO, DataStatisticsResponseMapper> {

    @Inject
    QueryAO queryAO;

    public void saveResponse(DataStatisticsResponseClientDTO request) {
        Optional<QueryEntity> queryEntityOptional = queryAO.findByGlobalUniqueID(request.getGlobalQueryId());
        if (queryEntityOptional.isEmpty()) {
            Log.errorf("Query with global id %s not found for client cant save statistics", request.getGlobalQueryId());
            return;
        }
        DataStatisticsResponseEntity statistics = new DataStatisticsResponseEntity();
        statistics.setStatistics(request.getStatistics());
        statistics.setQuery(queryEntityOptional.get());
        statistics.setRandomClinicId(request.getRandomClinicId());
        statistics.setRequestId(request.getRequestId());
        ao.persist(statistics);
    }

    public List<DataStatisticsResponseDTO> listForQuery(Long queryId, String keycloakId) {
        return mapper.entitiesToDtos(ao.listForQuery(queryId, keycloakId));
    }

    public List<DataStatisticsResponseDTO> listForRequest(UUID queryId, String keycloakId) {
        return mapper.entitiesToDtos(ao.listForRequest(queryId, keycloakId));
    }
}
