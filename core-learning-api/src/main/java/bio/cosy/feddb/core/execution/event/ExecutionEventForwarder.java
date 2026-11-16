package bio.cosy.feddb.core.execution.event;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.TransactionPhase;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.OnOverflow;

/**
 * Hands the execution events to their channels once the transaction that raised them committed, so a
 * consumer never reloads state that is not visible yet; outside a transaction they go right away.
 * Run finished events follow once something consumes them.
 */
@ApplicationScoped
public class ExecutionEventForwarder {

    @Inject
    @Channel(ExecutionChannels.RUN_STARTED)
    @OnOverflow(OnOverflow.Strategy.BUFFER)
    Emitter<RunStartedEvent> runStarted;

    @Inject
    @Channel(ExecutionChannels.STEP_CHANGED)
    @OnOverflow(OnOverflow.Strategy.BUFFER)
    Emitter<StepChangedEvent> stepChanged;

    @Inject
    @Channel(ExecutionChannels.ATTEMPT_LAUNCH)
    @OnOverflow(OnOverflow.Strategy.BUFFER)
    Emitter<AttemptLaunchEvent> attemptLaunch;

    @Inject
    @Channel(ExecutionChannels.ATTEMPT_FINISHED)
    @OnOverflow(OnOverflow.Strategy.BUFFER)
    Emitter<AttemptFinishedEvent> attemptFinished;

    @Inject
    @Channel(ExecutionChannels.STEP_FINISHED)
    @OnOverflow(OnOverflow.Strategy.BUFFER)
    Emitter<StepFinishedEvent> stepFinished;

    void onRunStarted(@Observes(during = TransactionPhase.AFTER_SUCCESS) RunStartedEvent event) {
        forward(runStarted, event);
    }

    void onStepChanged(@Observes(during = TransactionPhase.AFTER_SUCCESS) StepChangedEvent event) {
        forward(stepChanged, event);
    }

    void onAttemptLaunch(@Observes(during = TransactionPhase.AFTER_SUCCESS) AttemptLaunchEvent event) {
        forward(attemptLaunch, event);
    }

    void onAttemptFinished(@Observes(during = TransactionPhase.AFTER_SUCCESS) AttemptFinishedEvent event) {
        forward(attemptFinished, event);
    }

    void onStepFinished(@Observes(during = TransactionPhase.AFTER_SUCCESS) StepFinishedEvent event) {
        forward(stepFinished, event);
    }

    private <T> void forward(Emitter<T> emitter, T event) {
        emitter.send(event).whenComplete((ignored, e) -> {
            if (e != null) {
                Log.warnf(e, "Execution event %s was not handled", event);
            }
        });
    }
}
