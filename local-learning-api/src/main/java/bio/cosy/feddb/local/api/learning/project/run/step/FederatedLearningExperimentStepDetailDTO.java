package bio.cosy.feddb.local.api.learning.project.run.step;

import bio.cosy.feddb.core.api.file.FileDTO;
import bio.cosy.feddb.core.api.run.message.RunMessageLogDTO;
import bio.cosy.feddb.core.api.run.message.RunMessageMetricDTO;
import bio.cosy.feddb.core.api.workflow.node.WorkflowNodeDetailDTO;
import bio.cosy.feddb.local.api.learning.project.run.data.FederatedLearningExperimentStepDataDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
public class FederatedLearningExperimentStepDetailDTO extends FederatedLearningExperimentStepDTO {
    private List<RunMessageLogDTO> logs;
    private List<RunMessageMetricDTO> metrics;

    private LinkedHashMap<String, Object> result;

    private List<FileDTO> inputFiles;
    private List<FileDTO> outputFiles;

    public void addInputFile(FileDTO fileDTO) {
        if (this.inputFiles == null) {
            this.inputFiles = new ArrayList<>();
        }
        this.inputFiles.add(fileDTO);
    }

    public void addOutputFile(FileDTO fileDTO) {
        if (this.outputFiles == null) {
            this.outputFiles = new ArrayList<>();
        }
        this.outputFiles.add(fileDTO);
    }

    public void addResultEntry(String key, Object value) {
        if (this.result == null) {
            this.result = new LinkedHashMap<>();
        }
        this.result.put(key, value);
    }
}
