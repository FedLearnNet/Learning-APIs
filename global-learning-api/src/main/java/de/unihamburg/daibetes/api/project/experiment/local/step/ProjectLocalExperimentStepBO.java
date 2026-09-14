package de.unihamburg.daibetes.api.project.experiment.local.step;

import bio.cosy.feddb.core.api.run.RunStatusTypes;
import bio.cosy.feddb.core.api.workflow.base.step.BaseWorkflowStepBO;
import de.unihamburg.daibetes.api.project.ProjectAO;
import de.unihamburg.daibetes.api.project.ProjectEntity;
import de.unihamburg.daibetes.api.project.experiment.local.ProjectLocalExperimentEntity;
import de.unihamburg.daibetes.api.project.experiment.local.data.ProjectLocalExperimentStepDataBO;
import de.unihamburg.daibetes.api.project.experiment.local.data.ProjectLocalExperimentStepDataDTO;
import de.unihamburg.daibetes.api.project.experiment.local.message.ProjectLocalExperimentStepMessageBO;
import de.unihamburg.daibetes.api.workflow.WorkflowEntity;
import de.unihamburg.daibetes.api.workflow.node.WorkflowNodeEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotFoundException;
import org.apache.commons.lang3.StringUtils;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@ApplicationScoped
public class ProjectLocalExperimentStepBO extends BaseWorkflowStepBO<ProjectLocalExperimentStepDTO,
        ProjectLocalExperimentStepEntity,
        ProjectLocalExperimentStepAO,
        ProjectLocalExperimentStepMapper> {

    @Inject
    ProjectAO projectAO;

    @Inject
    ProjectLocalExperimentStepMessageBO messageBO;

    @Inject
    ProjectLocalExperimentStepDataBO dataBO;

    public Set<ProjectLocalExperimentStepEntity> createSteps(ProjectLocalExperimentEntity entity) {
        ProjectEntity project = projectAO.findById(entity.getProject().getId());
        if (project == null) {
            throw new NotFoundException("Project not found for experiment");
        }
        WorkflowEntity workflow = project.getWorkflow();
        if (workflow == null) {
            throw new NotFoundException("Project workflow not found for experiment");
        }
        HashSet<ProjectLocalExperimentStepEntity> steps = workflow.getNodes().stream()
                .map(node -> create(node, entity))
                .collect(HashSet::new, HashSet::add, HashSet::addAll);
        return steps;
    }

    private ProjectLocalExperimentStepEntity create(WorkflowNodeEntity node, ProjectLocalExperimentEntity experiment) {
        ProjectLocalExperimentStepEntity step = new ProjectLocalExperimentStepEntity();
        step.setExperiment(experiment);
        step.setWorkflowNode(node);
        step.setStepStatus(RunStatusTypes.PENDING);
        ao.persist(step);
        return step;
    }

    public ProjectLocalExperimentStepDetailDTO getDetailById(Long id) {
        Optional<ProjectLocalExperimentStepEntity> entity = ao.findByIdOptional(id);
        if (entity.isEmpty()) {
            throw new NotFoundException(String.format("Property with the ID %s not found", id));
        }
        ProjectLocalExperimentStepDetailDTO dto = mapper.entityToDetailDto(entity.get());
        dto.setMetrics(messageBO.findMetricByStepId(id));
        dto.setLogMessages(messageBO.findLogByStepId(id));

        List<ProjectLocalExperimentStepDataDTO> dataDTOs = dataBO.findByStep(id);
        for (ProjectLocalExperimentStepDataDTO data : dataDTOs) {
            if (data.getFile() != null && data.getStepInputId() != null && data.getStepInputId().equals(id)) {
                dto.addInputFile(data.getFile());
            }
            if (data.getFile() != null && data.getStepOutputId() != null && data.getStepOutputId().equals(id)) {
                dto.addOutputFile(data.getFile());
            }

            if (data.getFile() == null && StringUtils.isNotEmpty(data.getResult()) && StringUtils.isNotEmpty(data.getName())) {
                dto.addResultEntry(data.getName(), data.getResult());
            }
        }

        return dto;
    }
}
