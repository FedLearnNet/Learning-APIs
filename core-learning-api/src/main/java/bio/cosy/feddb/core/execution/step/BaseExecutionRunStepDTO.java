package bio.cosy.feddb.core.execution.step;

import bio.cosy.feddb.core.base.BaseDTO;
import bio.cosy.feddb.core.execution.attempt.BaseExecutionAttemptDTO;
import bio.cosy.feddb.core.execution.failure.ExecutionFailure;
import bio.cosy.feddb.core.execution.hyperparam.config.HyperParamSearchDTO;
import bio.cosy.feddb.core.execution.run.types.RunStatusTypes;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

@Data
@EqualsAndHashCode(callSuper = true)
public class BaseExecutionRunStepDTO extends BaseDTO {
    private UUID publicId;
    private Long runId;
    private UUID runPublicId;
    private Long workflowNodeId;
    private String name;
    private Instant startedAt;
    private Instant finishedAt;
    private RunStatusTypes status;
    private ExecutionFailure failure;
    private HyperParamSearchDTO search;
    private LinkedHashMap<String, Object> inputs;
    private Long selectedAttemptId;
    // Parent references remain IDs so nested run -> step -> attempt DTOs cannot recurse.
    private Set<BaseExecutionAttemptDTO> attempts = new LinkedHashSet<>();
}
