package bio.cosy.feddb.core.execution.attempt;

import bio.cosy.feddb.core.execution.BaseExecutionStatusDTO;
import bio.cosy.feddb.core.execution.message.ExecutionMessageMetricDTO;
import bio.cosy.feddb.core.execution.run.dto.RunMetaDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.LinkedHashMap;
import java.util.UUID;

@Data
@EqualsAndHashCode(callSuper = true)
public class BaseExecutionAttemptDTO extends BaseExecutionStatusDTO {
    private UUID publicId;
    private Long stepId;
    private UUID stepPublicId;
    private Long runId;
    private UUID runPublicId;
    private int trialIndex; //which parameter set this attempt runs
    private LinkedHashMap<String, Object> params;
    // Payload of the attempt's score message; its value ranks the trial.
    private ExecutionMessageMetricDTO scoreMetric;
    private String containerId;
    private Float progress;
    private RunMetaDTO meta;
}
