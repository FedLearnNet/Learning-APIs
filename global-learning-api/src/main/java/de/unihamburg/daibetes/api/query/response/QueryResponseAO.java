package de.unihamburg.daibetes.api.query.response;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

@ApplicationScoped
public class QueryResponseAO implements PanacheRepository<QueryResponseEntity> {


    public List<QueryResponseEntity> getAllByUser(String keycloakId) {
        return list("keycloakId", keycloakId);
    }

    public int getCountSumByUser(String keycloakId) {
        return list("keycloakId", keycloakId).stream().mapToInt(QueryResponseEntity::getCount).sum();
    }
}
