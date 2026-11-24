package de.unihamburg.daibetes.api.query.response;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

/**
 * Reads and writes the client answers to queries.
 */
@ApplicationScoped
public class QueryResponseAO implements PanacheRepository<QueryResponseEntity> {

    /**
     * Answers of clients that matched patients.
     */
    public List<QueryResponseEntity> listMatchedByQuery(Long queryId) {
        return list("query.id = ?1 and count > 0", queryId);
    }

    /**
     * All answers, also the ones without matches.
     */
    public long countByQuery(Long queryId) {
        return count("query.id = ?1", queryId);
    }

    /**
     * True when the client answered the query already.
     */
    public boolean existsByQueryAndConnection(Long queryId, String connectionId) {
        return count("query.id = ?1 and connectionId = ?2", queryId, connectionId) > 0;
    }
}
