package de.unihamburg.daibetes.api.analysis.worklfow.step;

import bio.cosy.feddb.core.api.model.workflow.DataAnalysisFileDTO;
import bio.cosy.feddb.core.execution.message.ExecutionMessageLogDTO;
import bio.cosy.feddb.core.execution.message.ExecutionMessageMetricDTO;
import bio.cosy.feddb.core.api.workflow.base.step.BaseWorkflowStepDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.LinkedHashMap;
import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
public class DataAnalysisWorkflowRunStepDTO extends BaseWorkflowStepDTO {
    private List<ExecutionMessageMetricDTO> metrics;
    private List<ExecutionMessageLogDTO> logs;

    private LinkedHashMap<String, Object> result;
    private LinkedHashMap<String, Object> inputs;
    private LinkedHashMap<String, Object> hyperParams;
    private List<DataAnalysisFileDTO> inputFiles;
    private List<DataAnalysisFileDTO> outputFiles;


    private String lastLog;
}
