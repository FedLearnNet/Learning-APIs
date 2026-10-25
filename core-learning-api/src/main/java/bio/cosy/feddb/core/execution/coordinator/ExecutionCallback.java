package bio.cosy.feddb.core.execution.coordinator;

import bio.cosy.feddb.core.security.Scope;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Where the apps of a run purpose talk back to: the scope of their API key and the callback path. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExecutionCallback {
    private Scope scope;
    private String path;
}
