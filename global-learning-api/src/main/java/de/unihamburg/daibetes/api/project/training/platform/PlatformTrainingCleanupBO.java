package de.unihamburg.daibetes.api.project.training.platform;

import de.unihamburg.daibetes.execution.experiment.ExecutionExperimentAO;
import de.unihamburg.daibetes.execution.run.ExecutionRunEntity;
import de.unihamburg.daibetes.execution.run.WorkflowExecutionBO;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class PlatformTrainingCleanupBO {

    @Inject
    ExecutionExperimentAO experimentAO;

    @Inject
    WorkflowExecutionBO workflowExecutionBO;

    public ExecutionRunEntity stop(ExecutionRunEntity run) {
        return workflowExecutionBO.stopTransactional(run.getId());
    }

    public void deleteTests(Long projectId) {
        experimentAO.listTestsOfProject(projectId).forEach(experiment -> {
            experiment.getRuns().forEach(run -> workflowExecutionBO.deleteTransactional(run.getId()));
            experimentAO.delete(experiment);
        });
    }
}
