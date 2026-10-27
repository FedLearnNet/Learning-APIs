package bio.cosy.feddb.core.execution.step;

import bio.cosy.feddb.core.execution.BaseExecutionStatusDTO;
import bio.cosy.feddb.core.execution.attempt.BaseExecutionAttemptDTO;
import bio.cosy.feddb.core.execution.hyperparam.config.HyperParamSearchDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

@Data
@EqualsAndHashCode(callSuper = true)
public class BaseExecutionRunStepDTO extends BaseExecutionStatusDTO {
    private UUID publicId;
    private Long runId;
    private UUID runPublicId;
    private Long workflowNodeId;
    private String name;
    private HyperParamSearchDTO search;
    private LinkedHashMap<String, Object> inputs;
    private LinkedHashMap<String, String> inputFilePaths;
    private Long selectedAttemptId;
    // Parent references remain IDs so nested run -> step -> attempt DTOs cannot recurse.
    private Set<BaseExecutionAttemptDTO> attempts = new LinkedHashSet<>();
}
