package de.unihamburg.daibetes.api.analysis;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.NotFoundException;

import java.util.List;
import java.util.Optional;


@ApplicationScoped
public class DataAnalysisAO implements PanacheRepository<DataAnalysisEntity> {


    public List<DataAnalysisEntity> getAll(String keycloakId) {
        return list("keycloakId = ?1", keycloakId);
    }

    public void deleteById(String keycloakId, Long id) {
        delete("keycloakId = ?1 and id != ?2", keycloakId, id);
    }

    public Optional<DataAnalysisEntity> findById(String keycloakId, Long id) {
        return find("keycloakId = ?1 and id = ?2", keycloakId, id).firstResultOptional();
    }

    public Optional<DataAnalysisEntity> findByRunId(Long runId) {
        return find("select d from DataAnalysisEntity d join d.runs r where r.id = ?1", runId).firstResultOptional();
    }

    public DataAnalysisEntity findById(Long id) {
        return find("id = ?1", id).firstResultOptional().orElseThrow(() -> new NotFoundException("Data analysis not found"));
    }
}
