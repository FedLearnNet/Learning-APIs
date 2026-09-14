package de.unihamburg.daibetes.api.model.version;

import bio.cosy.feddb.core.api.model.ModelPublishStatus;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Optional;


@ApplicationScoped
public class ModelVersionAO implements PanacheRepository<ModelVersionEntity> {

    public Optional<ModelVersionEntity> findLastUnpublishedVersionByExperimentId(Long experimentId) {
        return find("experiment.id = ?1 and publishStatus = ?2",
                Sort.by("majorVersion").descending()
                        .and("minorVersion").descending()
                        .and("patchVersion").descending(),
                experimentId, ModelPublishStatus.PRIVATE).firstResultOptional();
    }

    public Optional<ModelVersionEntity> findLastUnpublishedVersionByFederatedExperimentId(Long experimentId) {
        return find("federatedExperiment.id = ?1 and publishStatus = ?2",
                Sort.by("majorVersion").descending()
                        .and("minorVersion").descending()
                        .and("patchVersion").descending(),
                experimentId, ModelPublishStatus.PRIVATE).firstResultOptional();
    }

    public boolean existsById(Long id) {
        return count("id", id) > 0;
    }

    public Optional<ModelVersionEntity> findByModelAndFedEx(Long modelId, Long experimentId) {
        return find("model.id = ?1 and federatedExperiment.id = ?2",
                modelId, experimentId).firstResultOptional();
    }

    public List<ModelVersionEntity> getAllForModel(Long modelId) {
        return list("model.id",
                Sort.by("majorVersion").descending()
                        .and("minorVersion").descending()
                        .and("patchVersion").descending(),
                modelId);
    }

    public List<ModelVersionEntity> getPublicForModel(Long modelId) {
        return list("model.id = ?1 and publishStatus = ?2",
                Sort.by("majorVersion").descending()
                        .and("minorVersion").descending()
                        .and("patchVersion").descending(),
                modelId, ModelPublishStatus.PUBLISHED);
    }
}
