package de.unihamburg.daibetes.api.project.training.platform;

import bio.cosy.feddb.core.api.workflow.node.WorkflowNodeDetailDTO;
import bio.cosy.feddb.core.execution.ExecutionMode;
import bio.cosy.feddb.core.execution.ExecutionPurpose;
import bio.cosy.feddb.core.execution.executor.ExecutionStartDTO;
import bio.cosy.feddb.core.execution.orchestrator.WorkflowOrchestrator;
import de.unihamburg.daibetes.api.file.FileEntity;
import de.unihamburg.daibetes.api.project.ProjectAO;
import de.unihamburg.daibetes.api.project.ProjectEntity;
import de.unihamburg.daibetes.api.workflow.WorkflowBO;
import de.unihamburg.daibetes.api.workflow.WorkflowEntity;
import de.unihamburg.daibetes.execution.attempt.ExecutionAttemptEntity;
import de.unihamburg.daibetes.execution.experiment.ExecutionExperimentAO;
import de.unihamburg.daibetes.execution.experiment.ExecutionExperimentEntity;
import de.unihamburg.daibetes.execution.experiment.ExecutionExperimentMapper;
import de.unihamburg.daibetes.execution.file.ExecutionFileBO;
import de.unihamburg.daibetes.execution.run.ExecutionRunEntity;
import de.unihamburg.daibetes.execution.run.WorkflowExecutionBO;
import de.unihamburg.daibetes.execution.step.ExecutionRunStepEntity;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotFoundException;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@ApplicationScoped
public class PlatformTrainingSetupBO {

    @Inject
    ProjectAO projectAO;

    @Inject
    WorkflowBO workflowBO;

    @Inject
    WorkflowExecutionBO workflowExecutionBO;

    @Inject
    WorkflowOrchestrator orchestrator;

    @Inject
    ExecutionExperimentAO experimentAO;

    @Inject
    ExecutionExperimentMapper experimentMapper;

    @Inject
    ExecutionFileBO executionFileBO;

    /** An experiment of the project with a TRAINING run whose steps are the workflow's nodes. */
    public ExecutionRunEntity createRun(Long projectId, String name, String description, boolean test, String keycloakId) {
        ProjectEntity project = projectAO.findByIdOptional(projectId)
                .orElseThrow(() -> new NotFoundException("Project " + projectId + " not found"));
        WorkflowEntity workflow = Optional.ofNullable(project.getWorkflow())
                .orElseThrow(() -> new NotFoundException("Project " + projectId + " has no workflow"));

        ExecutionExperimentEntity experiment = experimentMapper.projectToExperimentEntity(name, description, test, keycloakId, project);
        experimentAO.persist(experiment);

        ExecutionRunEntity run = workflowExecutionBO.createTransactional(
                new ExecutionStartDTO(ExecutionPurpose.TRAINING, ExecutionMode.SINGLE, name), workflow.getNodes());
        run.setKeycloakId(keycloakId);
        // The run owns the link, the experiment only lists it.
        run.setExperiment(experiment);
        experiment.getRuns().add(run);
        return run;
    }

    /** Input files of the attempt's tool, each named by its input; every one is the project's data or handed over. */
    public LinkedHashMap<String, String> inputFilePathsOf(ExecutionAttemptEntity attempt) {
        LinkedHashMap<String, String> paths = new LinkedHashMap<>();
        nodeOf(attempt).ifPresent(node -> node.getAppInputConfig().forEach(input -> paths.put(input.getVariableName(), input.getFileName())));
        return paths;
    }

    /** Puts the project's data into the volume of a step that reads it: the first one or one the workflow feeds. */
    public void stageInputs(ExecutionAttemptEntity attempt) {
        ExecutionRunStepEntity step = attempt.getStep();
        FileEntity data = step.getRun().project().map(ProjectEntity::getFile).orElse(null);
        Optional<WorkflowNodeDetailDTO> node = nodeOf(attempt);
        if (data == null || node.isEmpty() || !readsProjectData(step, node.get())) {
            return;
        }
        File content = executionFileBO.loadFile(data);
        Map<String, FileEntity> inputs = new LinkedHashMap<>();
        node.get().getAppInputConfig().forEach(input -> {
            orchestrator.uploadFilesToVolume(step.getRun().getId(), attempt.getId(), content, input.getFileName());
            inputs.put(input.getVariableName(), data);
        });
        executionFileBO.linkInput(attempt, inputs);
        Log.infof("Staged the data of the project into attempt %d", attempt.getId());
    }

    private boolean readsProjectData(ExecutionRunStepEntity step, WorkflowNodeDetailDTO node) {
        return step.isFirstInExecutionOrder() || workflowBO.entityToDto(step.getWorkflowNode().getWorkflow()).feedsInputInto(node.getNodeId());
    }

    private Optional<WorkflowNodeDetailDTO> nodeOf(ExecutionAttemptEntity attempt) {
        if (attempt.getStep().getWorkflowNode() == null) {
            return Optional.empty();
        }
        return workflowBO.entityToDto(attempt.getStep().getWorkflowNode().getWorkflow()).nodeOf(attempt.getStep().getWorkflowNode().getNodeId());
    }
}
