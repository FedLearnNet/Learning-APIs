package de.unihamburg.daibetes.api.project.experiment.local;

import bio.cosy.feddb.core.api.workflow.base.experiment.BaseWorkflowExperimentAO;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class ProjectLocalExperimentAO extends BaseWorkflowExperimentAO<ProjectLocalExperimentEntity> {

    public List<ProjectLocalExperimentEntity> findByProjectId(Long id) {
        return list("project.id = ?1 AND isTestRun = ?2",
                Sort.by("createdAt", Sort.Direction.Descending),
                id, false);
    }

    public Optional<ProjectLocalExperimentEntity> getTestByProjectId(Long id) {
        return find("project.id = ?1 AND isTestRun = ?2",id, true).firstResultOptional();
    }

}
