package bio.cosy.feddb.core.execution.coordinator;

import bio.cosy.feddb.core.execution.step.BaseExecutionRunStepEntity;
import bio.cosy.feddb.core.api.workflow.node.BaseWorkflowNodeEntity;
import bio.cosy.feddb.core.api.workflow.WorkflowDTO;
import bio.cosy.feddb.core.api.workflow.base.BaseWorkflowEngine;
import bio.cosy.feddb.core.api.workflow.node.WorkflowNodeDetailDTO;
import bio.cosy.feddb.core.execution.ExecutionPurpose;
import bio.cosy.feddb.core.execution.attempt.BaseExecutionAttemptAO;
import bio.cosy.feddb.core.execution.attempt.BaseExecutionAttemptEntity;
import bio.cosy.feddb.core.execution.event.AttemptFinishedEvent;
import bio.cosy.feddb.core.execution.event.AttemptLaunchEvent;
import bio.cosy.feddb.core.execution.event.ExecutionEventPublisher;
import bio.cosy.feddb.core.execution.failure.ExecutionFailure;
import bio.cosy.feddb.core.execution.failure.FailureCategory;
import bio.cosy.feddb.core.execution.orchestrator.OrchestratorGateway;
import bio.cosy.feddb.core.execution.run.types.RunStatusTypes;
import bio.cosy.feddb.core.security.ToolApiKeyService;
import bio.cosy.feddb.core.services.orch.dto.StartWorkflowNodeDTO;
import bio.cosy.feddb.core.services.orch.dto.WorkflowNodeInputActionDTO;
import io.quarkus.logging.Log;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Starts the container of a new attempt and stops it once the attempt ended. The container start is a
 * slow remote call, so it runs between two short transactions instead of holding a lock inside one.
 */
public abstract class BaseAttemptLauncher<
        Attempt extends BaseExecutionAttemptEntity<?, ?>,
        AttemptAO extends BaseExecutionAttemptAO<Attempt>
        > {

    private static final BaseWorkflowEngine ENGINE = new BaseWorkflowEngine();

    @Inject
    OrchestratorGateway orchestrator;

    @Inject
    ToolApiKeyService toolApiKeyService;

    @Inject
    ExecutionEventPublisher events;

    @Inject
    public AttemptAO attemptAO;

    // Per run purpose, set by the BO and websocket that drive its apps.
    private final Map<ExecutionPurpose, ExecutionCallback> callbacks;

    protected BaseAttemptLauncher(Map<ExecutionPurpose, ExecutionCallback> callbacks) {
        this.callbacks = Map.copyOf(callbacks);
    }

    /**
     * What the attempt runs: its workflow node, or for a step without one the app or model it names, with
     * the app or model detail that decides its image and network access.
     */
    protected abstract WorkflowNodeDetailDTO targetOf(Attempt attempt);

    public void onAttemptLaunch(AttemptLaunchEvent event) {
        Long attemptId = event.getAttemptId();
        try {
            // Callback path and start request of the claimed attempt.
            Map.Entry<String, StartWorkflowNodeDTO> launch = prepareTransactional(attemptId);
            if (launch == null) {
                return;
            }
            // What the app reads is in its volume before it starts.
            stageInputsTransactional(attemptId);
            String containerId = orchestrator.startWorkflow(launch.getValue(), launch.getKey());
            if (!startedTransactional(attemptId, containerId)) {
                stop(attemptId, containerId);
            }
        } catch (RuntimeException e) {
            Log.errorf(e, "Could not launch attempt %d", attemptId);
            failTransactional(attemptId, e);
        }
    }

    // Works from the event alone, the attempt may be deleted already. A container that was still starting
    // is stopped by the launch itself once it sees the attempt ended.
    public void onAttemptFinished(AttemptFinishedEvent event) {
        // No callback means no key was ever issued for it.
        Optional.ofNullable(callbacks.get(event.getPurpose()))
                .ifPresent(callback -> toolApiKeyService.revoke(callback.getScope(), event.getAttemptId()));
        stop(event.getAttemptId(), event.getContainerId());
    }

    // Claims the attempt, so a duplicated launch event cannot start a second container.
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    protected Map.Entry<String, StartWorkflowNodeDTO> prepareTransactional(Long attemptId) {
        Optional<Attempt> locked = attemptAO.findByIdWrite(attemptId);
        if (locked.isEmpty() || locked.get().getStatus() != RunStatusTypes.PENDING) {
            Log.debugf("Ignoring launch of attempt %d, it is gone or already launched", attemptId);
            return null;
        }
        Attempt attempt = locked.get();
        ExecutionCallback callback = callbacks.get(attempt.getStep().getRun().getPurpose());
        // Without a callback the attempt runs elsewhere, e.g. in the developer's own app engine.
        if (callback == null) {
            return null;
        }
        attempt.setStatus(RunStatusTypes.INITIALIZED);

        String apiKey = toolApiKeyService.issue(callback.getScope(), attempt.getId());
        StartWorkflowNodeDTO start = ENGINE.getStartDTO(targetOf(attempt), apiKey);
        // Every attempt gets its own volume, and the app connects back with the attempt id.
        start.setWorkflowId(attempt.getStep().getRun().getId());
        start.setWorkflowNodeId(attempt.getId());
        start.upsertEnv("APP_ID", String.valueOf(attempt.getId()));
        // Outputs of earlier steps the app reads, copied from their attempts' volumes.
        start.setInputs(inputsOf(attempt));
        start.setIsFistNode(start.getInputs().isEmpty());
        customizeStart(attempt, start);
        return Map.entry(callback.getPath(), start);
    }

    /** Adjusts the container start, e.g. whether the app uploads its results; nothing by default. */
    protected void customizeStart(Attempt attempt, StartWorkflowNodeDTO start) {
    }

    /** The workflow of the attempt's node, whose connections hand over outputs; none by default. */
    protected WorkflowDTO workflowOf(Attempt attempt) {
        return null;
    }

    // Every connection into the step's node from another node hands over a file of that node's selected attempt.
    private List<WorkflowNodeInputActionDTO> inputsOf(Attempt attempt) {
        BaseWorkflowNodeEntity node = attempt.getStep().getWorkflowNode();
        WorkflowDTO workflow = node == null ? null : workflowOf(attempt);
        if (workflow == null) {
            return List.of();
        }
        List<? extends BaseExecutionRunStepEntity<?, ?, ?>> steps = attempt.getStep().getRun().getSteps();
        return workflow.getConnections().stream()
                .filter(connection -> !connection.isInputConnection() && node.getNodeId().equals(connection.getInputNodeId()))
                .map(connection -> steps.stream()
                        .filter(step -> step.getWorkflowNode() != null
                                && step.getWorkflowNode().getNodeId().equals(connection.getOutputNodeId())
                                && step.getSelectedAttempt() != null)
                        .findFirst()
                        .map(step -> {
                            WorkflowNodeInputActionDTO input = new WorkflowNodeInputActionDTO();
                            input.setInputNodeId(step.getSelectedAttempt().getId());
                            input.setOriginalFileName(connection.getOutputFileName());
                            input.setNewFileName(connection.getInputFileName());
                            return input;
                        }))
                .flatMap(Optional::stream)
                .toList();
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    protected void stageInputsTransactional(Long attemptId) {
        attemptAO.findByIdOptional(attemptId).ifPresent(this::stageInputs);
    }

    protected void stageInputs(Attempt attempt) {
    }

    // False when the attempt ended while its container was starting, so the container is not needed anymore.
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    protected boolean startedTransactional(Long attemptId, String containerId) {
        Attempt attempt = attemptAO.findByIdWrite(attemptId).orElse(null);
        if (attempt == null || attempt.hasEnded()) {
            return false;
        }
        attempt.setContainerId(containerId);
        attempt.setStartedAt(Instant.now());
        // The app may have connected and reported already.
        if (attempt.getStatus() == RunStatusTypes.INITIALIZED) {
            attempt.setStatus(RunStatusTypes.STARTED);
        }
        Log.infof("Attempt %d runs in container %s", attemptId, containerId);
        return true;
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    protected void failTransactional(Long attemptId, RuntimeException e) {
        attemptAO.findByIdWrite(attemptId)
                .filter(attempt -> !attempt.hasEnded())
                .ifPresent(attempt -> {
                    attempt.end(RunStatusTypes.ERROR, new ExecutionFailure(FailureCategory.INFRASTRUCTURE,
                            "Container could not be started: " + e.getMessage()));
                    events.attemptFinished(attempt);
                });
    }

    // Volumes stay, a later step may still read the attempt's outputs.
    private void stop(Long attemptId, String containerId) {
        if (containerId == null) {
            return;
        }
        try {
            orchestrator.cleanup(containerId, false);
        } catch (RuntimeException e) {
            Log.warnf(e, "Could not stop container %s of attempt %d", containerId, attemptId);
        }
    }
}
