package de.unihamburg.daibetes.api.app.config.output;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class FederatedAppOutputConfigAO implements PanacheRepository<FederatedAppOutputConfigEntity> {


    public List<FederatedAppOutputConfigEntity> findByAppVersionId(Long appId) {
        return list("federatedAppVersion.id = ?1", appId);
    }

    public Optional<FederatedAppOutputConfigEntity> findByNameAndAppVersionId(Long appId, String name) {
        return find("federatedAppVersion.id = ?1 and name = ?2", appId, name).firstResultOptional();
    }

    public void deleteNotIn(List<Long> ids, Long appId) {
        delete("federatedAppVersion.id = ?1 and id not in ?2", appId, ids);
    }
}
