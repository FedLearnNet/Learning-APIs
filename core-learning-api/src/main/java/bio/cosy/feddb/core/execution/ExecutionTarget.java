package bio.cosy.feddb.core.execution;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ExecutionTarget {
    private ExecutionLocation location;
    private String deploymentId;
}
