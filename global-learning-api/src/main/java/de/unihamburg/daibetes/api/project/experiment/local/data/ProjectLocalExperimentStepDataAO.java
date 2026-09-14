package de.unihamburg.daibetes.api.project.experiment.local.data;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

@ApplicationScoped
public class ProjectLocalExperimentStepDataAO implements PanacheRepository<ProjectLocalExperimentStepDataEntity> {

    public List<ProjectLocalExperimentStepDataEntity> findByStep(Long id) {
        return list("stepInput.id = ?1 OR stepOutput.id = ?2", id, id);
    }

}
