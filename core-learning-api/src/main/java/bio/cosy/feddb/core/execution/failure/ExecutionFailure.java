package bio.cosy.feddb.core.execution.failure;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ExecutionFailure {
    private ExecutionFailureType type;
    private FailureCategory category;
    private String message;

    public ExecutionFailure(FailureCategory category, String message) {
        this.category = category;
        this.message = message;
    }
}
