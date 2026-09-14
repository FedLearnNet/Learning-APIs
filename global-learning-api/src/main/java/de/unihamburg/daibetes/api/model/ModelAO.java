package de.unihamburg.daibetes.api.model;

import bio.cosy.feddb.core.api.model.ModelPublishStatus;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Optional;
import java.util.UUID;


@ApplicationScoped
public class ModelAO implements PanacheRepository<ModelEntity> {

    public List<ModelEntity> getAllPublished() {
        return list("publishStatus = ?1 ORDER BY id DESC", ModelPublishStatus.PUBLISHED);
    }

    public Optional<ModelEntity> findByVersionIdAndPublished(Long id) {
        return find("id = ?1 AND publishStatus = ?2", id, ModelPublishStatus.PUBLISHED)
                .firstResultOptional();
    }

    public Optional<ModelEntity> findByAppVersionId(Long appVersionId) {
        return find("federatedAppVersion.id = ?1", appVersionId)
                .firstResultOptional();
    }

    public Optional<ModelEntity> findByUniqueModelId(UUID uniqueModelId) {
        return find("uniqueModelId", uniqueModelId)
                .firstResultOptional();
    }
}
