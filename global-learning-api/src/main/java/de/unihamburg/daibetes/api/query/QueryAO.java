package de.unihamburg.daibetes.api.query;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;

/**
 * Reads and writes queries; queries of a user are sorted newest first.
 */
@ApplicationScoped
public class QueryAO implements PanacheRepository<QueryEntity> {

    // id breaks ties of versions created in the same instant
    private static final String VERSIONS_FOR_USER = "groupId = ?1 and keycloakId = ?2 order by createdAt desc, id desc";

    /**
     * All queries of the user, every version included.
     */
    public List<QueryEntity> listByUser(String keycloakId) {
        return list("keycloakId = ?1 order by createdAt desc, id desc", keycloakId);
    }

    /**
     * Query of the user, empty for queries of others.
     */
    public Optional<QueryEntity> findByIdForUser(Long id, String keycloakId) {
        return find("id = ?1 and keycloakId = ?2", id, keycloakId).firstResultOptional();
    }

    /**
     * Query by the id the clients know.
     */
    public Optional<QueryEntity> findByGlobalUniqueId(String globalUniqueId) {
        return find("globalUniqueId = ?1", globalUniqueId).firstResultOptional();
    }

    /**
     * Query by the id the clients know, row locked until the transaction ends.
     */
    public Optional<QueryEntity> findForUpdateByGlobalUniqueId(String globalUniqueId) {
        return find("globalUniqueId = ?1", globalUniqueId).withLock(LockModeType.PESSIMISTIC_WRITE).firstResultOptional();
    }

    /**
     * Versions of one query of the user.
     */
    public List<QueryEntity> listVersionsForUser(String groupId, String keycloakId) {
        return list(VERSIONS_FOR_USER, groupId, keycloakId);
    }

    /**
     * Newest version of one query of the user.
     */
    public Optional<QueryEntity> findLatestVersionForUser(String groupId, String keycloakId) {
        return find(VERSIONS_FOR_USER, groupId, keycloakId).firstResultOptional();
    }

    /**
     * Marks the query released with its result.
     */
    public void updateResult(QueryEntity entity, int result) {
        entity.setResult(result);
        entity.setHasResult(true);
        flush();
    }
}
