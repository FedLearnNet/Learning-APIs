package bio.cosy.feddb.core.api.workflow.base;

import lombok.NoArgsConstructor;

@NoArgsConstructor
public class WorkflowException extends RuntimeException {
    public WorkflowException(String message) {
        super(message);
    }

    public WorkflowException(String message, Throwable cause) {
        super(message, cause);
    }

    public WorkflowException(Throwable cause) {
        super(cause);
    }
}
