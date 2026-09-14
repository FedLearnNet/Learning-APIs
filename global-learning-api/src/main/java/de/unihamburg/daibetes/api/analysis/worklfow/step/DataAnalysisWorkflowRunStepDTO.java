package de.unihamburg.daibetes.api.analysis.worklfow.step;

import bio.cosy.feddb.core.api.model.workflow.DataAnalysisFileDTO;
import bio.cosy.feddb.core.api.run.message.RunMessageLogDTO;
import bio.cosy.feddb.core.api.run.message.RunMessageMetricDTO;
import bio.cosy.feddb.core.api.workflow.base.step.BaseWorkflowStepDTO;
import de.unihamburg.daibetes.api.analysis.worklfow.message.DataAnalysisWorkflowRunMessagesDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.LinkedHashMap;
import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
public class DataAnalysisWorkflowRunStepDTO extends BaseWorkflowStepDTO {
    private List<RunMessageMetricDTO> metrics;
    private List<RunMessageLogDTO> logs;

    private LinkedHashMap<String, Object> result;
    private LinkedHashMap<String, Object> inputs;
    private LinkedHashMap<String, Object> hyperParams;
    private List<DataAnalysisFileDTO> inputFiles;
    private List<DataAnalysisFileDTO> outputFiles;


    private String lastLog;
}
