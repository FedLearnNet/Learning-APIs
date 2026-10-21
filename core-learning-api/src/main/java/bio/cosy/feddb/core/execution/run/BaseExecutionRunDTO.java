package bio.cosy.feddb.core.execution.run;

import bio.cosy.feddb.core.base.BaseDTO;
import bio.cosy.feddb.core.execution.ExecutionLocation;
import bio.cosy.feddb.core.execution.ExecutionMode;
import bio.cosy.feddb.core.execution.ExecutionPurpose;
import bio.cosy.feddb.core.execution.failure.ExecutionFailure;
import bio.cosy.feddb.core.execution.run.types.RunStatusTypes;
import bio.cosy.feddb.core.execution.step.BaseExecutionRunStepDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
@EqualsAndHashCode(callSuper = true)
public class BaseExecutionRunDTO extends BaseDTO {
    private UUID publicId;
    private String name;
    private ExecutionPurpose purpose;
    private ExecutionMode mode;
    private ExecutionLocation location;
    private RunStatusTypes status;
    private Instant startedAt;
    private Instant finishedAt;
    private ExecutionFailure failure;
    private List<BaseExecutionRunStepDTO> steps = new ArrayList<>();
}
