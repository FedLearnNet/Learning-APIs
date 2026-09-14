package bio.cosy.feddb.core.services.orch.dto;


/**
 * Enum representing the possible run statuses of a container as part of a workflow or process.
 * This can be used to track and manage the lifecycle or state of a container during its execution.
 * States from the OrchAPI
 *
 * Statuses:
 * - INIT: Indicates the container is initialized and ready to start.
 * - RUNNING: Indicates the container is currently running.
 * - STARTED: Indicates the container has been started.
 * - STOPPED: Indicates the container has been stopped.
 * - FAILED: Indicates the container execution has failed.
 * - COMPLETED: Indicates the container execution completed successfully.
 * - ABORTED: Indicates the container execution was aborted.
 * - UNKNOWN: Represents an unknown or uninitialized status.
 */
public enum ContainerRunStatus {
    INIT, RUNNING, STARTED, STOPPED, FAILED, COMPLETED, ABORTED, UNKNOWN;
}
