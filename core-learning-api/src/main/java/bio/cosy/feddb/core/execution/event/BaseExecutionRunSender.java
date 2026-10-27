package bio.cosy.feddb.core.execution.event;

import bio.cosy.feddb.core.execution.ExecutionPurpose;
import bio.cosy.feddb.core.execution.attempt.BaseExecutionAttemptAO;
import bio.cosy.feddb.core.execution.run.BaseExecutionRunAO;
import bio.cosy.feddb.core.execution.run.BaseExecutionRunEntity;
import bio.cosy.feddb.core.execution.step.BaseExecutionRunStepAO;
import io.quarkus.logging.Log;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.virtual.threads.VirtualThreads;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.operators.multi.processors.UnicastProcessor;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.TransactionPhase;
import jakarta.inject.Inject;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Flow;

/**
 * Announces changed runs per purpose. Apps report per log line, so changes are collected for a short window
 * and each changed run is announced once per window, to observers of {@link ExecutionRunsChangedEvent}.
 */
public abstract class BaseExecutionRunSender<
        RunAO extends BaseExecutionRunAO<?>,
        StepAO extends BaseExecutionRunStepAO<?>,
        AttemptAO extends BaseExecutionAttemptAO<?>> {

    private static final Duration WINDOW = Duration.ofMillis(250);

    @Inject
    protected RunAO runAO;

    @Inject
    StepAO stepAO;

    @Inject
    AttemptAO attemptAO;

    @Inject
    Event<ExecutionRunsChangedEvent> runsChanged;

    @Inject
    @VirtualThreads
    ExecutorService virtualThreads;

    // Fed by the observers of every committing thread, so access is serialized.
    private final UnicastProcessor<ExecutionRunChange> changes = UnicastProcessor.create();
    private final Flow.Processor<ExecutionRunChange, ExecutionRunChange> changed = changes.serialized();


    @PostConstruct
    void announceChangedRuns() {
        changes.group().intoLists().every(WINDOW)
                .filter(window -> !window.isEmpty())
                // Reading the runs needs a transaction of its own, which cannot begin on the committing thread.
                .onItem().transformToUniAndConcatenate(window -> Uni.createFrom().item(() -> {
                            QuarkusTransaction.requiringNew().run(() -> runsOf(window).forEach((purpose, runIds) ->
                                    runsChanged.fire(new ExecutionRunsChangedEvent(purpose, runIds))));
                            return window.size();
                        })
                        .runSubscriptionOn(virtualThreads)
                        .onFailure().recoverWithItem(failure -> {
                            Log.debugf(failure, "Could not announce the runs of %d execution changes", window.size());
                            return 0;
                        }))
                .subscribe().with(count -> {
                }, failure -> Log.errorf(failure, "Changed runs are no longer announced"));
    }

    // The runs behind the changes, by purpose; empty for what is gone.
    private Map<ExecutionPurpose, List<Long>> runsOf(List<ExecutionRunChange> changes) {
        Map<ExecutionPurpose, Set<Long>> runs = new EnumMap<>(ExecutionPurpose.class);
        changes.forEach(change -> runOf(change).ifPresent(run ->
                runs.computeIfAbsent(run.getPurpose(), purpose -> new LinkedHashSet<>()).add(run.getId())));
        Map<ExecutionPurpose, List<Long>> runIds = new EnumMap<>(ExecutionPurpose.class);
        runs.forEach((purpose, ids) -> runIds.put(purpose, List.copyOf(ids)));
        return runIds;
    }

    private Optional<? extends BaseExecutionRunEntity<?>> runOf(ExecutionRunChange change) {
        if (change.getRunId() != null) {
            return runAO.findByIdOptional(change.getRunId());
        }
        if (change.getStepId() != null) {
            return stepAO.findByIdOptional(change.getStepId()).map(step -> step.getRun());
        }
        return attemptAO.findByIdOptional(change.getAttemptId()).map(attempt -> attempt.getStep().getRun());
    }

    // Execution events once their transaction committed.
    void onRunStarted(@Observes(during = TransactionPhase.AFTER_SUCCESS) RunStartedEvent event) {
        changed.onNext(new ExecutionRunChange().setRunId(event.getRunId()));
    }

    void onAttemptUpdated(@Observes(during = TransactionPhase.AFTER_SUCCESS) AttemptUpdatedEvent event) {
        changed.onNext(new ExecutionRunChange().setAttemptId(event.getAttemptId()));
    }

    void onAttemptFinished(@Observes(during = TransactionPhase.AFTER_SUCCESS) AttemptFinishedEvent event) {
        changed.onNext(new ExecutionRunChange().setAttemptId(event.getAttemptId()));
    }

    void onStepChanged(@Observes(during = TransactionPhase.AFTER_SUCCESS) StepChangedEvent event) {
        changed.onNext(new ExecutionRunChange().setStepId(event.getStepId()));
    }

    void onStepFinished(@Observes(during = TransactionPhase.AFTER_SUCCESS) StepFinishedEvent event) {
        changed.onNext(new ExecutionRunChange().setStepId(event.getStepId()));
    }

    void onRunFinished(@Observes(during = TransactionPhase.AFTER_SUCCESS) RunFinishedEvent event) {
        changed.onNext(new ExecutionRunChange().setRunId(event.getRunId()));
    }
}
