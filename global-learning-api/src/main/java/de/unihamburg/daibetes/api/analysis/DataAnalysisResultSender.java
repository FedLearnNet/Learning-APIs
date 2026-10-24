package de.unihamburg.daibetes.api.analysis;

import bio.cosy.feddb.core.execution.event.AttemptFinishedEvent;
import bio.cosy.feddb.core.execution.event.AttemptUpdatedEvent;
import bio.cosy.feddb.core.execution.event.StepChangedEvent;
import bio.cosy.feddb.core.execution.event.StepFinishedEvent;
import de.unihamburg.daibetes.api.analysis.run.DataAnalysisRunChange;
import de.unihamburg.daibetes.api.analysis.run.DataAnalysisRunDTO;
import de.unihamburg.daibetes.api.analysis.run.DataAnalysisRunResultBO;
import io.quarkus.logging.Log;
import io.quarkus.virtual.threads.VirtualThreads;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.operators.multi.processors.UnicastProcessor;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.TransactionPhase;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.OnOverflow;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Flow;

import static de.unihamburg.daibetes.api.analysis.run.DataAnalysisRunServiceImpl.DATA_ANALYSIS_RUN_CHANNEL;

/**
 * Sends analysis runs to the streams that follow them. Apps report per log line, so changes are
 * collected for a short window and each changed run is read and sent once per window.
 */
@ApplicationScoped
public class DataAnalysisResultSender {

    private static final Duration WINDOW = Duration.ofMillis(250);

    @Inject
    @Channel(DATA_ANALYSIS_RUN_CHANNEL)
    @OnOverflow(OnOverflow.Strategy.DROP)
    Emitter<DataAnalysisRunDTO> infoEmitter;

    @Inject
    DataAnalysisRunResultBO runResultBO;

    @Inject
    @VirtualThreads
    ExecutorService virtualThreads;

    // Fed by the observers of every committing thread, so access is serialized.
    private final UnicastProcessor<DataAnalysisRunChange> changes = UnicastProcessor.create();
    private final Flow.Processor<DataAnalysisRunChange, DataAnalysisRunChange> changed = changes.serialized();

    @PostConstruct
    void sendChangedRuns() {
        changes.group().intoLists().every(WINDOW)
                .filter(window -> !window.isEmpty())
                // Reading the runs needs a transaction of its own, which cannot begin on the committing thread.
                .onItem().transformToMultiAndConcatenate(window -> Uni.createFrom().item(() -> runResultBO.runsOf(window))
                        .runSubscriptionOn(virtualThreads)
                        .onFailure().recoverWithItem(failure -> {
                            Log.debugf(failure, "Could not read the runs of %d execution changes", window.size());
                            return List.of();
                        })
                        .onItem().transformToMulti(runs -> Multi.createFrom().iterable(runs)))
                .subscribe().with(this::sendMessage, failure -> Log.errorf(failure, "Analysis runs are no longer sent"));
    }

    // Execution events once their transaction committed.
    void onAttemptUpdated(@Observes(during = TransactionPhase.AFTER_SUCCESS) AttemptUpdatedEvent event) {
        changed.onNext(DataAnalysisRunChange.ofAttempt(event.getAttemptId()));
    }

    void onAttemptFinished(@Observes(during = TransactionPhase.AFTER_SUCCESS) AttemptFinishedEvent event) {
        changed.onNext(DataAnalysisRunChange.ofAttempt(event.getAttemptId()));
    }

    void onStepChanged(@Observes(during = TransactionPhase.AFTER_SUCCESS) StepChangedEvent event) {
        changed.onNext(DataAnalysisRunChange.ofStep(event.getStepId()));
    }

    void onStepFinished(@Observes(during = TransactionPhase.AFTER_SUCCESS) StepFinishedEvent event) {
        changed.onNext(DataAnalysisRunChange.ofStep(event.getStepId()));
    }

    public void sendMessage(DataAnalysisRunDTO info) {
        try {
            infoEmitter.send(info);
            Log.debug("SSE event sent: " + info.getId());
        } catch (Exception e) {
            Log.debug("Attempted to send SSE event, but client connection was already closed.", e);
        }
    }
}
