package bio.cosy.feddb.core.execution.failure;

import lombok.Data;

@Data
public class ExecutionFailure {
    private ExecutionFailureType type;
    private FailureCategory category;
    private String message;
}
