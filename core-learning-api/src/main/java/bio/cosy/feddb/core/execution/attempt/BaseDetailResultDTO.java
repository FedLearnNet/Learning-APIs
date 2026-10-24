package bio.cosy.feddb.core.execution.attempt;

import bio.cosy.feddb.core.base.BaseDTO;
import bio.cosy.feddb.core.execution.message.ExecutionMessageLogDTO;
import bio.cosy.feddb.core.execution.message.ExecutionMessageMetricDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * An attempt with what its app read, reported and wrote; File is how the purpose shows its files.
 * Logs and metrics are only filled for a detail view, lists and streams carry the last log.
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class BaseDetailResultDTO<File extends BaseDTO> extends BaseExecutionAttemptDTO {
    // The uploaded result, parsed.
    private LinkedHashMap<String, Object> outputData = new LinkedHashMap<>();
    private String lastLog;
    private List<ExecutionMessageLogDTO> logs = new ArrayList<>();
    private List<ExecutionMessageMetricDTO> metrics = new ArrayList<>();
    private List<File> inputFiles = new ArrayList<>();
    private List<File> outputFiles = new ArrayList<>();
}
