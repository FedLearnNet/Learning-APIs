package de.unihamburg.daibetes.api.analysis.worklfow;

import bio.cosy.feddb.core.api.workflow.base.experiment.BaseWorkflowExperimentDTO;
import de.unihamburg.daibetes.api.analysis.worklfow.step.DataAnalysisWorkflowRunStepDTO;
import de.unihamburg.daibetes.api.project.experiment.local.step.ProjectLocalExperimentStepDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
public class DataAnalysisWorkflowRunDTO extends BaseWorkflowExperimentDTO {
    private Long workflowId;
    private Long dataAnalysisId;

    private List<DataAnalysisWorkflowRunStepDTO> steps;
}
