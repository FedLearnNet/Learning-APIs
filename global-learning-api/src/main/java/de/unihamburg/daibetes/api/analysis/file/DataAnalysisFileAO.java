package de.unihamburg.daibetes.api.analysis.file;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Optional;


@ApplicationScoped
public class DataAnalysisFileAO implements PanacheRepository<DataAnalysisFileEntity> {

    public List<DataAnalysisFileEntity> getAll(String keycloakId) {
        return list("and dataAnalysis.keycloakId = ?1", keycloakId);
    }

    public List<DataAnalysisFileEntity> getAll(Long dataAnalysisId, String keycloakId) {
        return list("dataAnalysis.id = ?1 and dataAnalysis.keycloakId = ?2", dataAnalysisId, keycloakId);
    }

    public List<DataAnalysisFileEntity> getAllForWorkflow(Long workflowId, String keycloakId) {
        return list("workflowStep.experiment.id = ?1 and dataAnalysis.keycloakId = ?2", workflowId, keycloakId);
    }

    public Optional<DataAnalysisFileEntity> getById(Long id, String keycloakId) {
        return find("id = ?1 and dataAnalysis.keycloakId = ?2", id, keycloakId).firstResultOptional();
    }

    public Optional<DataAnalysisFileEntity> getByFileName(String fileName, String keycloakId) {
        return find("path = ?1 and dataAnalysis.keycloakId = ?2", fileName, keycloakId).firstResultOptional();
    }

    public Optional<DataAnalysisFileEntity> getByFileName(String fileName) {
        return find("path = ?1", fileName).firstResultOptional();
    }

    public Optional<DataAnalysisFileEntity> getById(Long id, Long dataAnalysisId, String keycloakId) {
        return find("id = ?1 and dataAnalysis.id = ?2 and dataAnalysis.keycloakId = ?3", id, dataAnalysisId, keycloakId).firstResultOptional();
    }

    public Optional<DataAnalysisFileEntity> getByFileId(Long id, Long dataAnalysisId, String keycloakId) {
        return find("file.id = ?1 and dataAnalysis.id = ?2 and dataAnalysis.keycloakId = ?3", id, dataAnalysisId, keycloakId).firstResultOptional();
    }


}
