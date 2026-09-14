package de.unihamburg.daibetes.api.project.experiment.local;

import bio.cosy.feddb.core.api.workflow.base.experiment.BaseWorkflowExperimentDTO;
import de.unihamburg.daibetes.api.project.experiment.local.step.ProjectLocalExperimentStepDTO;
import de.unihamburg.daibetes.api.runs.experiment.ExperimentDiagramConfigDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
public class ProjectLocalExperimentDTO extends BaseWorkflowExperimentDTO {

    private String name;
    private String description;

    private boolean isTestRun;

    //needs to be implemented
    private List<ExperimentDiagramConfigDTO> diagramConfigs;

    private List<ProjectLocalExperimentStepDTO> steps;

    //Project which is linked to a workflow
    private Long projectId;

    //workflow
    private Long workflowId;
    private Long workflowVersion;

    //UUID generated in the business layer, not shown to user;
    //can be used if project support hyperparam tuning or multiple experiment runs grouping
    //For now not used
    private String groupId;
}
