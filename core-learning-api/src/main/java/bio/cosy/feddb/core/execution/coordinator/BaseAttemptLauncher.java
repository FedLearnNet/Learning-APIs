package bio.cosy.feddb.core.execution.coordinator;

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
import io.quarkus.logging.Log;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.inject.Inject;

import java.time.Instant;
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
            Map.Entry<String, StartWorkflowNodeDTO> launch = QuarkusTransaction.requiringNew().call(() -> prepare(attemptId));
            if (launch == null) {
                return;
            }
            String containerId = orchestrator.startWorkflow(launch.getValue(), launch.getKey());
            if (!QuarkusTransaction.requiringNew().call(() -> started(attemptId, containerId))) {
                stop(attemptId, containerId);
            }
        } catch (RuntimeException e) {
            fail(attemptId, e);
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
    private Map.Entry<String, StartWorkflowNodeDTO> prepare(Long attemptId) {
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
        return Map.entry(callback.getPath(), start);
    }

    // False when the attempt ended while its container was starting, so the container is not needed anymore.
    private boolean started(Long attemptId, String containerId) {
        Attempt attempt = attemptAO.findByIdWrite(attemptId).orElse(null);
        if (attempt == null || RunStatusTypes.isFinalStatus(attempt.getStatus())) {
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

    private void fail(Long attemptId, RuntimeException e) {
        Log.errorf(e, "Could not launch attempt %d", attemptId);
        QuarkusTransaction.requiringNew().run(() -> attemptAO.findByIdWrite(attemptId)
                .filter(attempt -> !RunStatusTypes.isFinalStatus(attempt.getStatus()))
                .ifPresent(attempt -> {
                    attempt.setStatus(RunStatusTypes.ERROR);
                    attempt.setFinishedAt(Instant.now());
                    attempt.setFailure(new ExecutionFailure(FailureCategory.INFRASTRUCTURE,
                            "Container could not be started: " + e.getMessage()));
                    events.attemptFinished(attempt);
                }));
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
