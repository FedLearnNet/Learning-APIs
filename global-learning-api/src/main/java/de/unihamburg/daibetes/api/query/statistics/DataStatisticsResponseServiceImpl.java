package de.unihamburg.daibetes.api.query.statistics;

import de.unihamburg.daibetes.api.auth.UserIdentity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class DataStatisticsResponseServiceImpl implements DataStatisticsResponseService {

    @Inject
    UserIdentity userIdentity;

    @Inject
    DataStatisticsResponseBO bo;

    @Override
    public List<DataStatisticsResponseDTO> listForQuery(Long queryId) {
        return bo.listForQuery(queryId, userIdentity.getKeycloakId());
    }

    @Override
    public List<DataStatisticsResponseDTO> listForRequest(UUID queryId) {
        return bo.listForRequest(queryId, userIdentity.getKeycloakId());
    }
}
