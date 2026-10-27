package bio.cosy.feddb.core.execution.coordinator;

import bio.cosy.feddb.core.security.Scope;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Where the apps of a run purpose talk back to: the scope of their API key and their mode under /execution. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExecutionCallback {
    private Scope scope;
    // The app connects to /execution/{mode}/{attemptId}/app and uploads below /execution/{mode}/{attemptId}/upload.
    private String mode;

    public String getPath() {
        return "execution/" + mode;
    }
}
