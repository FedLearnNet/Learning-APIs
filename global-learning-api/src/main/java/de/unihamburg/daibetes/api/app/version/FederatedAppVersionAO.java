package de.unihamburg.daibetes.api.app.version;

import bio.cosy.feddb.core.api.app.PublishStatus;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class FederatedAppVersionAO implements PanacheRepository<FederatedAppVersionEntity> {

    public List<FederatedAppVersionEntity> listForAudit(String keycloakId, long minAuditCount) {
        return find(
                "from FederatedAppVersionEntity v " +
                        "where v.versionPublishStatus = ?1 " +
                        "and (select count(a) from ToolAuditEntity a where a.appVersion = v) < ?2 " +
                        "and not exists (" +
                        "   select 1 from FederatedAppAuthorEntity aa " +
                        "   where aa.federatedApp = v.federatedApp and aa.keycloakId = ?3" +
                        ")",
                PublishStatus.PUBLISHED,
                minAuditCount,
                keycloakId
        ).list();
    }

    public FederatedAppVersionEntity findLastVersionByAppId(Long appId) {
        return this.findLastVersionByAppIdOptional(appId).orElse(null);
    }

    public Optional<FederatedAppVersionEntity> findLastVersionByAppIdOptional(Long appId) {
        return find("federatedApp.id",
                Sort.by("majorVersion").descending()
                        .and("minorVersion").descending()
                        .and("patchVersion").descending(),
                appId).firstResultOptional();
    }

    public List<FederatedAppVersionEntity> findAllByAppId(Long appId) {
        return list("federatedApp.id",
                Sort.by("majorVersion").descending()
                        .and("minorVersion").descending()
                        .and("patchVersion").descending(),
                appId);
    }

    public FederatedAppVersionEntity findByAppIdAndVersion(Long appId, String version) {
        return find("federatedApp.id = ?1 AND appVersion = ?2", appId, version).firstResult();
    }

    public boolean existsByAppId(Long appId) {
        return count("federatedApp.id", appId) > 0;
    }

    public boolean existsByPublishHash(String publishHash, UUID uniqueAppId) {
        return count("publishHash = ?1 AND federatedApp.uniqueAppId = ?2", publishHash, uniqueAppId) > 0;
    }

    public Optional<FederatedAppVersionEntity> findByPublishHash(String publishHash, UUID uniqueAppId) {
        return find("publishHash = ?1 AND federatedApp.uniqueAppId = ?2", publishHash, uniqueAppId).firstResultOptional();
    }

    public Optional<FederatedAppVersionEntity> findLastVersionByAppIdOptional(UUID uniqueAppId) {
        return find("federatedApp.uniqueAppId",
                Sort.by("majorVersion").descending()
                        .and("minorVersion").descending()
                        .and("patchVersion").descending(),
                uniqueAppId).firstResultOptional();
    }
}
