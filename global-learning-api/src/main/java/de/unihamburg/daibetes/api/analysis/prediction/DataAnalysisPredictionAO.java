package de.unihamburg.daibetes.api.analysis.prediction;

import bio.cosy.feddb.core.api.run.RunStatusTypes;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Optional;


@ApplicationScoped
public class DataAnalysisPredictionAO implements PanacheRepository<DataAnalysisPredictionEntity> {

    public List<DataAnalysisPredictionEntity> getAll(String keycloakId) {
        return list("keycloakId = ?1", keycloakId);
    }

    public Optional<DataAnalysisPredictionEntity> getById(Long id, String keycloakId) {
        return find("id = ?1 and keycloakId = ?2", id, keycloakId).firstResultOptional();
    }

    public Optional<DataAnalysisPredictionEntity> getByContainerId(String containerId, String keycloakId) {
        return find("containerId = ?1 and keycloakId = ?2", containerId, keycloakId).firstResultOptional();
    }

    public List<DataAnalysisPredictionEntity> getAllPending() {
        return list("status", RunStatusTypes.PENDING);
    }

    public List<DataAnalysisPredictionEntity> getAllForModel(String keycloakId, Long modelId) {
        return list("keycloakId = ?1 and subModel.modelVersion.model.id = ?2", keycloakId, modelId);
    }


    public void deleteByRunId(Long modelVersionId, Long id) {
        delete("subModel.modelVersion.id = ?1 and subModel.id != ?2", modelVersionId, id);
    }
}
