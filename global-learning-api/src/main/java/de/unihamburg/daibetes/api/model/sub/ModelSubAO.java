package de.unihamburg.daibetes.api.model.sub;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.NotFoundException;

import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class ModelSubAO implements PanacheRepository<ModelSubEntity> {

    public ModelSubEntity findByIdAuthorChecked(Long modelSubId, String keycloakId) {
        return find("from ModelSubEntity s where s.id = ?1 and exists ("
                        + "select 1 from ModelAccessEntity a where a.model = s.modelVersion.model and a.keycloakId = ?2)",
                modelSubId, keycloakId)
                .firstResultOptional()
                .orElseThrow(() -> new NotFoundException("Sub model " + modelSubId + " not found"));
    }

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

    public void deleteByRunId(Long modelVersionId, Long id) {
        delete("modelVersion.id = ?1 and id != ?2", modelVersionId, id);
    }

    public Optional<ModelSubEntity> findByPublishHash(String publishHash, UUID uniqueAppId) {
        return find("publishHash = ?1 AND modelVersion.model.federatedAppVersion.federatedApp.uniqueAppId = ?2", publishHash, uniqueAppId).firstResultOptional();
    }
}
