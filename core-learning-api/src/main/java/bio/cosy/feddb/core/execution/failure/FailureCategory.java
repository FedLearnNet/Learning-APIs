package bio.cosy.feddb.core.execution.failure;

public enum FailureCategory {
    VALIDATION,
    AUTHORIZATION,
    INFRASTRUCTURE,
    TOOL,
    CANCELLED,
    UNKNOWN
}