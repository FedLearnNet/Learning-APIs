package de.unihamburg.daibetes.api.store.rating;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

@ApplicationScoped
public class StoreRatingRatingAO implements PanacheRepository<StoreRatingEntity> {

    public StoreRatingEntity findByUserIdAndAppId(String keycloakId, Long appId) {
        return find("keycloakId = ?1 and federatedApp.id = ?2", keycloakId, appId).firstResult();
    }

    public StoreRatingEntity findByUserIdAndModelVersionId(String keycloakId, Long modelVersionId) {
        return find("keycloakId = ?1 and modelVersion.id = ?2", keycloakId, modelVersionId).firstResult();
    }

    public List<StoreRatingEntity> findAllByModelVersionId(Long modelVersionId) {
        return list("modelVersion.id", modelVersionId);
    }

    public List<StoreRatingEntity> findAllByAppId(Long appId) {
        return list("federatedApp.id", appId);
    }
}
