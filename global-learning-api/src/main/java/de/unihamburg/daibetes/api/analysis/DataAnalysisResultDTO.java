package de.unihamburg.daibetes.api.analysis;

import bio.cosy.feddb.core.api.model.prediction.DataAnalysisPredictionDTO;
import bio.cosy.feddb.core.api.model.prediction.DataAnalysisRunModesEnum;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
public class DataAnalysisResultDTO extends DataAnalysisPredictionDTO {
    private DataAnalysisRunModesEnum runMode;

    //for workflows
    private Long maxWorkflowSteps;
    private Long currentWorkflowStep;
    private Long currentWorkflowStepId;
    private Long workflowRunId;
    private Long executionOrder;

    //TODO private List<DataAnalysisResultDTO> prevSteps;
}
