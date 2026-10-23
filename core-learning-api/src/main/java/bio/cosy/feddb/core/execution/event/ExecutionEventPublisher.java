package bio.cosy.feddb.core.execution.event;

import bio.cosy.feddb.core.execution.attempt.BaseExecutionAttemptEntity;
import bio.cosy.feddb.core.execution.run.types.RunStatusTypes;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;


@ApplicationScoped
public class ExecutionEventPublisher {

    @Inject
    Event<RunStartedEvent> runStarted;

    @Inject
    Event<StepChangedEvent> stepChanged;

    @Inject
    Event<AttemptLaunchEvent> attemptLaunch;

    @Inject
    Event<AttemptUpdatedEvent> attemptUpdated;

    @Inject
    Event<AttemptFinishedEvent> attemptFinished;

    @Inject
    Event<StepFinishedEvent> stepFinished;

    @Inject
    Event<RunFinishedEvent> runFinished;

    public void runStarted(Long runId) {
        runStarted.fire(new RunStartedEvent(runId));
    }

    public void stepChanged(Long stepId) {
        stepChanged.fire(new StepChangedEvent(stepId));
    }

    public void attemptLaunch(Long attemptId) {
        attemptLaunch.fire(new AttemptLaunchEvent(attemptId));
    }

    public void attemptUpdated(Long attemptId) {
        attemptUpdated.fire(new AttemptUpdatedEvent(attemptId));
    }

    // Also a change of its step, so the step coordinator plans the next trial.
    public void attemptFinished(BaseExecutionAttemptEntity<?, ?> attempt) {
        attemptFinished.fire(new AttemptFinishedEvent(attempt.getId(), attempt.getStatus(), attempt.getContainerId(),
                attempt.getStep().getRun().getPurpose()));
        stepChanged(attempt.getStep().getId());
    }

    public void stepFinished(Long stepId, RunStatusTypes status) {
        stepFinished.fire(new StepFinishedEvent(stepId, status));
    }

    public void runFinished(Long runId, RunStatusTypes status) {
        runFinished.fire(new RunFinishedEvent(runId, status));
    }
}
