package bio.cosy.feddb.core.execution.coordinator;

import bio.cosy.feddb.core.execution.event.ExecutionEventPublisher;
import bio.cosy.feddb.core.execution.event.RunStartedEvent;
import bio.cosy.feddb.core.execution.event.StepFinishedEvent;
import bio.cosy.feddb.core.execution.failure.ExecutionFailure;
import bio.cosy.feddb.core.execution.failure.FailureCategory;
import bio.cosy.feddb.core.execution.run.BaseExecutionRunAO;
import bio.cosy.feddb.core.execution.run.BaseExecutionRunEntity;
import bio.cosy.feddb.core.execution.run.types.RunStatusTypes;
import bio.cosy.feddb.core.execution.step.BaseExecutionRunStepAO;
import bio.cosy.feddb.core.execution.step.BaseExecutionRunStepEntity;
import io.quarkus.logging.Log;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Moves a run through its steps one at a time in workflow execution order, on run started and
 * step finished. Decides from the step statuses alone, so a repeated event changes nothing.
 */
public abstract class BaseWorkflowCoordinator<
        Run extends BaseExecutionRunEntity<Step>,
        Step extends BaseExecutionRunStepEntity<Run, ?, ?>,
        RunAO extends BaseExecutionRunAO<Run>,
        StepAO extends BaseExecutionRunStepAO<Step>
        > {

    // Waiting for their turn; a step that started belongs to the step coordinator until it ends.
    private static final Set<RunStatusTypes> WAITING = EnumSet.of(RunStatusTypes.PENDING, RunStatusTypes.INITIALIZED);

    @Inject
    ExecutionEventPublisher events;

    @Inject
    public RunAO runAO;

    @Inject
    public StepAO stepAO;

    @Transactional
    public void onRunStarted(RunStartedEvent event) {
        advance(event.getRunId());
    }

    @Transactional
    public void onStepFinished(StepFinishedEvent event) {
        stepAO.findByIdOptional(event.getStepId()).ifPresentOrElse(
                step -> advance(step.getRun().getId()),
                () -> Log.debugf("Ignoring finished step %d, it is gone", event.getStepId()));
    }

    private void advance(Long runId) {
        Optional<Run> locked = runAO.findByIdWrite(runId);
        if (locked.isEmpty() || RunStatusTypes.isFinalStatus(locked.get().getStatus())) {
            Log.debugf("Ignoring run %d, it is gone or already done", runId);
            return;
        }
        Run run = locked.get();
        List<Step> steps = run.getStepsInExecutionOrder();

        Optional<Step> ended = steps.stream()
                .filter(step -> step.hasEnded() && step.getStatus() != RunStatusTypes.FINISHED)
                .findFirst();
        if (ended.isPresent()) {
            stopWaiting(steps);
            finish(run, ended.get().getStatus(), failureOf(ended.get()));
            return;
        }

        if (steps.stream().anyMatch(step -> !step.hasEnded() && !WAITING.contains(step.getStatus()))) {
            return;
        }

        Optional<Step> next = steps.stream().filter(step -> WAITING.contains(step.getStatus())).findFirst();
        if (next.isPresent()) {
            run.begin(RunStatusTypes.RUNNING);
            events.stepChanged(next.get().getId());
            Log.infof("Run %d hands step %d to its coordinator", run.getId(), next.get().getId());
            return;
        }

        finish(run, RunStatusTypes.FINISHED, null);
    }

    private void stopWaiting(List<Step> steps) {
        steps.stream()
                .filter(step -> WAITING.contains(step.getStatus()))
                .forEach(step -> step.setStatus(RunStatusTypes.STOPPED));
    }

    private void finish(Run run, RunStatusTypes status, ExecutionFailure failure) {
        run.end(status, failure);
        events.runFinished(run.getId(), status);
        Log.infof("Run %d finished as %s", run.getId(), status);
    }

    private ExecutionFailure failureOf(BaseExecutionRunStepEntity<?, ?, ?> step) {
        ExecutionFailure cause = step.getFailure();
        ExecutionFailure failure = new ExecutionFailure();
        failure.setCategory(cause != null && cause.getCategory() != null ? cause.getCategory() : FailureCategory.UNKNOWN);
        failure.setMessage("Step " + (step.getName() != null ? step.getName() : step.getId()) + " ended as "
                + step.getStatus() + (cause != null && cause.getMessage() != null ? ": " + cause.getMessage() : ""));
        return failure;
    }
}
