package bio.cosy.feddb.local.execution.step;

import bio.cosy.feddb.core.execution.step.BaseExecutionRunStepDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class ExecutionRunStepDTO extends BaseExecutionRunStepDTO {
    // What a step without a workflow node runs.
    private Long globalAppVersionId;
    private Long globalModelVersionId;
    private String appImage;
    private String appName;
    private Long transformerId;
}
