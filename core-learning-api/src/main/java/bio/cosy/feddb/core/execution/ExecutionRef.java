package bio.cosy.feddb.core.execution;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ExecutionRef {
    private ExecutionTarget target;
    private UUID runId;
    private UUID stepId;
    private UUID attemptId;
}
