package de.unihamburg.daibetes.agent.audit;

import de.unihamburg.daibetes.api.app.version.FederatedAppVersionEntity;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.LockModeType;

import java.util.Date;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class ToolLlmAuditAO implements PanacheRepository<ToolLlmAuditEntity> {

    private static final List<ToolLlmAuditStatus> ACTIVE = List.of(ToolLlmAuditStatus.PENDING, ToolLlmAuditStatus.RUNNING);

    public List<ToolLlmAuditEntity> listByVersionId(Long appVersionId) {
        return find("appVersion.id", Sort.descending("id"), appVersionId).list();
    }

    public Optional<ToolLlmAuditEntity> findLatestByVersionIdOptional(Long appVersionId) {
        return find("appVersion.id", Sort.descending("id"), appVersionId).firstResultOptional();
    }

    public Optional<ToolLlmAuditEntity> findActiveByVersionIdOptional(Long appVersionId) {
        return find("appVersion.id = ?1 and status in ?2", Sort.descending("id"), appVersionId, ACTIVE)
                .firstResultOptional();
    }

    public Optional<ToolLlmAuditEntity> findFinishedByFingerprintOptional(Long appVersionId, String fingerprint) {
        return find("appVersion.id = ?1 and fingerprint = ?2 and status = ?3", Sort.descending("id"),
                appVersionId, fingerprint, ToolLlmAuditStatus.FINISHED).firstResultOptional();
    }

    public List<Date> listRequestTimesSince(String keycloakId, Date since) {
        return getEntityManager().createQuery("select a.createdAt from ToolLlmAuditEntity a "
                        + "where a.keycloakId = :keycloakId and a.createdAt >= :since order by a.createdAt", Date.class)
                .setParameter("keycloakId", keycloakId)
                .setParameter("since", since)
                .getResultList();
    }

    public long countActive() {
        return count("status in ?1", ACTIVE);
    }

    // Serialises concurrent requests for the same version
    public FederatedAppVersionEntity lockVersion(Long appVersionId) {
        return getEntityManager().find(FederatedAppVersionEntity.class, appVersionId, LockModeType.PESSIMISTIC_WRITE);
    }

    public int failActive(String message) {
        return update("status = ?1, errorMessage = ?2, finishedAt = ?3 where status in ?4",
                ToolLlmAuditStatus.FAILED, message, new Date(), ACTIVE);
    }
}
