package de.unihamburg.daibetes.api.app.config.hyperparam;

import de.unihamburg.daibetes.api.app.config.input.FederatedAppInputConfigEntity;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class FederatedAppHyperParamConfigAO implements PanacheRepository<FederatedAppHyperParamConfigEntity> {

    public List<FederatedAppHyperParamConfigEntity> findByAppVersionId(Long appId) {
        return list("federatedAppVersion.id = ?1", appId);
    }

    public Optional<FederatedAppHyperParamConfigEntity> findByNameAndAppVersionId(Long appId, String name) {
        return find("federatedAppVersion.id = ?1 and name = ?2", appId, name).firstResultOptional();
    }


    public void deleteNotIn(List<Long> ids, Long appId) {
        delete("federatedAppVersion.id = ?1 and id not in ?2", appId, ids);
    }
}
