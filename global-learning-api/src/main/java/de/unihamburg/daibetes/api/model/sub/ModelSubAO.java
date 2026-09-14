package de.unihamburg.daibetes.api.model.sub;

import de.unihamburg.daibetes.api.app.version.FederatedAppVersionEntity;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Optional;
import java.util.UUID;


@ApplicationScoped
public class ModelSubAO implements PanacheRepository<ModelSubEntity> {


    public Optional<ModelSubEntity> findByRunId(Long experimentRunId) {
        return find("experimentRun.id", experimentRunId).firstResultOptional();
    }

    public Optional<ModelSubEntity> findByFederatedRunId(Long experimentRunId) {
        return find("federatedExperiment.id", experimentRunId).firstResultOptional();
    }


    public Optional<ModelSubEntity> findByModelVersionAndFedEx(Long modelVersionId, Long experimentId) {
        return find("modelVersion.id = ?1 and federatedExperiment.id = ?2",
                modelVersionId, experimentId).firstResultOptional();
    }
    public Optional<ModelSubEntity> findByModelVersionAndEx(Long modelVersionId, Long experimentId) {
        return find("modelVersion.id = ?1 and experimentRun.id = ?2",
                modelVersionId, experimentId).firstResultOptional();
    }

    public Optional<ModelSubEntity> findByRunModelVersionId(Long modelVersionId) {
        return find("modelVersion.id", modelVersionId).firstResultOptional();
    }

    public boolean isModelIdCorrespondingToModelSubId(Long modelId, Long modelSubId) {
        return find("modelVersion.model.id = ?1 and id = ?2", modelId, modelSubId).count() > 0;
    }


    public void deleteByRunId(Long modelVersionId, Long id) {
        delete("modelVersion.id = ?1 and id != ?2", modelVersionId, id);
    }

    public Optional<ModelSubEntity> findByPublishHash(String publishHash, UUID uniqueAppId) {
        return find("publishHash = ?1 AND modelVersion.model.federatedAppVersion.federatedApp.uniqueAppId = ?2", publishHash, uniqueAppId).firstResultOptional();
    }

}
