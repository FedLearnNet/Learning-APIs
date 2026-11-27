package bio.cosy.feddb.core.execution;

public enum ExecutionMode {
    SINGLE, // For one execution participant, locally or platform
    TRE, // For trusted resource env.
    FEDERATED // For federated execution
}
