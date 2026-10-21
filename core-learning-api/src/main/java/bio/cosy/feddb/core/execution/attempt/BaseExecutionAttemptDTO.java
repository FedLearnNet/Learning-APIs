package bio.cosy.feddb.core.execution.attempt;

import bio.cosy.feddb.core.base.BaseDTO;
import bio.cosy.feddb.core.execution.failure.ExecutionFailure;
import bio.cosy.feddb.core.execution.run.dto.RunMetaDTO;
import bio.cosy.feddb.core.execution.run.types.RunStatusTypes;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.Instant;
import java.util.UUID;

@Data
@EqualsAndHashCode(callSuper = true)
public class BaseExecutionAttemptDTO extends BaseDTO {
    private UUID publicId;
    private Long stepId;
    private UUID stepPublicId;
    private Long runId;
    private UUID runPublicId;
    private int attemptNumber;
    private RunStatusTypes status;
    private String containerId;
    private Float progress;
    private Instant startedAt;
    private Instant finishedAt;
    private ExecutionFailure failure;
    private RunMetaDTO meta;
}
