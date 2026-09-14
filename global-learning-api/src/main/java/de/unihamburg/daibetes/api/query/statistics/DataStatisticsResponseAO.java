package de.unihamburg.daibetes.api.query.statistics;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class DataStatisticsResponseAO implements PanacheRepository<DataStatisticsResponseEntity> {


    public List<DataStatisticsResponseEntity> listForQuery(Long queryId, String keycloakId) {
        return find("query.keycloakId = ?1 and query.id = ?2", keycloakId, queryId).list();
    }

    public List<DataStatisticsResponseEntity> listForRequest(UUID requestId, String keycloakId) {
        return find("query.keycloakId = ?1 and requestId = ?2", keycloakId, requestId).list();
    }
}
