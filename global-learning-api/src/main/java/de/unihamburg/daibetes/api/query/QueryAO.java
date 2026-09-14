package de.unihamburg.daibetes.api.query;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Date;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class QueryAO implements PanacheRepository<QueryEntity> {

    public List<QueryEntity> getAllByUser(String keycloakId) {
        return find("keycloakId = ?1 order by createdAt desc, id desc", keycloakId).list();
    }

    public Optional<QueryEntity> findByIdForUser(Long id, String keycloakId) {
        return find("id = ?1 and keycloakId = ?2", id, keycloakId).firstResultOptional();
    }

    public Optional<QueryEntity> findByGlobalUniqueID(String id) {
        return find("globalUniqueId = ?1", id).firstResultOptional();
    }

    public List<QueryEntity> findAllVersionsByGroupIdForUser(String groupId, String keycloakId) {
        return find("groupId = ?1 and keycloakId = ?2 order by createdAt desc, id desc", groupId, keycloakId).list();
    }

    public Optional<QueryEntity> findLatestVersionByGroupIdForUser(String groupId, String keycloakId) {
        return find("groupId = ?1 and keycloakId = ?2 order by createdAt desc, id desc", groupId, keycloakId)
                .firstResultOptional();
    }

    public long updateErrorByGlobalUniqueId(String globalUniqueId, String error) {
        return update("error = ?1, updatedAt = ?2, version = version + 1 where globalUniqueId = ?3",
                error, new Date(), globalUniqueId);
    }

    public long updateCountByGlobalUniqueId(String globalUniqueId, int count) {
        return update("""
                        result = coalesce(result, 0) + ?1,
                        hasResult = true,
                        updatedAt = ?2,
                        version = version + 1
                        where globalUniqueId = ?3
                        """,
                count,
                new Date(),
                globalUniqueId);
    }
}
