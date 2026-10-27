package bio.cosy.feddb.core.execution;

import bio.cosy.feddb.core.base.BaseDTO;
import bio.cosy.feddb.core.execution.failure.ExecutionFailure;
import bio.cosy.feddb.core.execution.run.types.RunStatusTypes;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.Instant;

@Data
@EqualsAndHashCode(callSuper = true)
public class BaseExecutionStatusDTO extends BaseDTO {
    private RunStatusTypes status;
    private Instant startedAt;
    private Instant finishedAt;
    private ExecutionFailure failure;

    @JsonIgnore
    public boolean hasEnded() {
        return RunStatusTypes.isFinalStatus(status);
    }
}
