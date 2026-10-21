package de.unihamburg.daibetes.api.project.experiment.local.message;

import bio.cosy.feddb.core.execution.message.BaseExecutionMessageAO;
import bio.cosy.feddb.core.execution.message.RunMessageTypes;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

@ApplicationScoped
public class ProjectLocalExperimentStepMessageAO extends BaseExecutionMessageAO<ProjectLocalExperimentStepMessageEntity> {

    public List<ProjectLocalExperimentStepMessageEntity> findByExperiment(Long id) {
        return list("step.experiment.id", id);
    }

    public List<ProjectLocalExperimentStepMessageEntity> findByExperiment(Long id, RunMessageTypes type) {
        return list("step.experiment.id = ?1 and type = ?2",
                Sort.by("createdAt", Sort.Direction.Descending),
                id, type);
    }

    public List<ProjectLocalExperimentStepMessageEntity> findByStep(Long id, RunMessageTypes type) {
        return list("step.id = ?1 and type = ?2",
                Sort.by("createdAt", Sort.Direction.Descending),
                id, type);
    }

}
