package de.unihamburg.daibetes.api.project.experiment.local.data;

import bio.cosy.feddb.core.api.file.FileDTO;
import bio.cosy.feddb.core.base.BaseBo;
import de.unihamburg.daibetes.api.file.FileAO;
import de.unihamburg.daibetes.api.file.FileBO;
import de.unihamburg.daibetes.api.file.FileEntity;
import de.unihamburg.daibetes.api.project.experiment.local.step.ProjectLocalExperimentStepAO;
import de.unihamburg.daibetes.api.project.experiment.local.step.ProjectLocalExperimentStepEntity;
import de.unihamburg.daibetes.api.workflow.connection.WorkflowConnectionAO;
import de.unihamburg.daibetes.api.workflow.connection.WorkflowConnectionBO;
import de.unihamburg.daibetes.api.workflow.connection.WorkflowConnectionEntity;
import de.unihamburg.daibetes.services.WorkflowOrchestratorBO;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.io.File;
import java.util.List;

@ApplicationScoped
public class ProjectLocalExperimentStepDataBO extends BaseBo<ProjectLocalExperimentStepDataDTO, ProjectLocalExperimentStepDataEntity, ProjectLocalExperimentStepDataAO, ProjectLocalExperimentStepDataMapper> {

    @Inject
    FileBO fileBO;

    @Inject
    FileAO fileAO;

    @Inject
    WorkflowOrchestratorBO workflowOrchestratorBO;

    @Inject
    ProjectLocalExperimentStepAO stepAO;

    @Inject
    WorkflowConnectionAO workflowConnectionAO;

    @Inject
    WorkflowConnectionBO workflowConnectionBO;

    public List<ProjectLocalExperimentStepDataDTO> findByStep(Long id) {
        return mapper.entitiesToDtos(ao.findByStep(id));
    }

    /*
     * Creates files and associates them with the given step in a new transaction.
     * Create them as output and inputs directly, so the output can be used as input in the next step.
     */
    public void createForStep(List<File> files, Long workflowNodeId, Long experimentId, ProjectLocalExperimentStepEntity step) {
        if (files == null || files.isEmpty() || workflowNodeId == null) {
            return;
        }
        List<FileDTO> internalFiles = fileBO.create(files);
        List<WorkflowConnectionEntity> edges = workflowConnectionAO.findByWorkflowOutputNodeId(workflowNodeId);

        for (FileDTO file : internalFiles) {
            this.create(file, workflowNodeId, experimentId, step, edges);
        }
    }

    public void saveResults(Long stepId) {
        ao.flush();
        ProjectLocalExperimentStepEntity step = stepAO.findById(stepId);
        if (step == null) {
            Log.errorf("Could not find step with id %s", stepId);
            return;
        }
        if (step.getExperiment().isTestRun()) {
            Log.infof("Skipping saving results for test run step with id %s", stepId);
            return;
        }
        Long workflowNodeId = step.getWorkflowNode().getId();
        Long experimentId = step.getExperiment().getId();
        List<File> files = workflowOrchestratorBO.getFiles(experimentId, step.getId());

        createForStep(files, workflowNodeId, experimentId, step);
    }


    public ProjectLocalExperimentStepDataDTO create(FileDTO file,
                                                    Long workflowNodeId,
                                                    Long experimentId,
                                                    ProjectLocalExperimentStepEntity step,
                                                    List<WorkflowConnectionEntity> edges) {
        if (file == null || workflowNodeId == null) {
            return null;
        }
        String fileName = file.getFileName();
        WorkflowConnectionEntity edge = workflowConnectionBO.filterForFileName(edges, fileName);
        if (edge == null) {
            Log.warnf("No workflow connection found for workflow node id %d and fileName %s", workflowNodeId, fileName);
        }
        ProjectLocalExperimentStepDataEntity entity = new ProjectLocalExperimentStepDataEntity();
        entity.setFile(fileAO.findById(file.getId()));
        entity.setStepOutput(step);
        if (edge != null) {
            Long inputNodeId = edge.getInputNode().getId();
            stepAO.findByWorkflowNodeId(experimentId, inputNodeId)
                    .ifPresent(entity::setStepInput);
        }
        ao.persist(entity);
        return mapper.entityToDto(entity);
    }

    public void createForStep(FileEntity fileEntity,
                              ProjectLocalExperimentStepEntity step) {
        ProjectLocalExperimentStepDataEntity entity = new ProjectLocalExperimentStepDataEntity();
        entity.setFile(fileEntity);
        entity.setStepInput(step);
        ao.persist(entity);
        mapper.entityToDto(entity);
    }

}
