package de.unihamburg.daibetes.api.analysis;

import bio.cosy.feddb.core.execution.event.AttemptFinishedEvent;
import bio.cosy.feddb.core.execution.event.AttemptUpdatedEvent;
import bio.cosy.feddb.core.execution.event.StepChangedEvent;
import bio.cosy.feddb.core.execution.event.StepFinishedEvent;
import de.unihamburg.daibetes.api.analysis.run.DataAnalysisRunDTO;
import de.unihamburg.daibetes.api.analysis.run.DataAnalysisRunResultBO;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.TransactionPhase;
import jakarta.inject.Inject;
import org.eclipse.microprofile.context.ManagedExecutor;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.OnOverflow;

import java.util.Optional;
import java.util.function.Supplier;

import static de.unihamburg.daibetes.api.analysis.run.DataAnalysisRunServiceImpl.DATA_ANALYSIS_RUN_CHANNEL;

@ApplicationScoped
public class DataAnalysisResultSender {
    @Inject
    @Channel(DATA_ANALYSIS_RUN_CHANNEL)
    @OnOverflow(OnOverflow.Strategy.DROP)
    Emitter<DataAnalysisRunDTO> infoEmitter;

    @Inject
    DataAnalysisRunResultBO runResultBO;

    @Inject
    ManagedExecutor executor;

    // Execution events once their transaction committed. Reading the run needs a transaction of its
    // own, which cannot begin while the committed one completes, so it is read on another thread.
    void onAttemptUpdated(@Observes(during = TransactionPhase.AFTER_SUCCESS) AttemptUpdatedEvent event) {
        sendLater(() -> runResultBO.runOfAttempt(event.getAttemptId()));
    }

    void onAttemptFinished(@Observes(during = TransactionPhase.AFTER_SUCCESS) AttemptFinishedEvent event) {
        sendLater(() -> runResultBO.runOfAttempt(event.getAttemptId()));
    }

    void onStepChanged(@Observes(during = TransactionPhase.AFTER_SUCCESS) StepChangedEvent event) {
        sendLater(() -> runResultBO.runOfStep(event.getStepId()));
    }

    void onStepFinished(@Observes(during = TransactionPhase.AFTER_SUCCESS) StepFinishedEvent event) {
        sendLater(() -> runResultBO.runOfStep(event.getStepId()));
    }

    // Empty when the run is gone or is no data analysis run.
    private void sendLater(Supplier<Optional<DataAnalysisRunDTO>> run) {
        executor.runAsync(() -> run.get().ifPresent(this::sendMessage))
                .exceptionally(e -> {
                    Log.debugf(e, "Could not send the run of an execution event");
                    return null;
                });
    }


    public void sendMessage(DataAnalysisRunDTO info) {
        try {
            infoEmitter.send(info);
            Log.debug("SSE event sent: " + info);
        } catch (Exception e) {
            Log.debug("Attempted to send SSE event, but client connection was already closed.", e);
        }
    }

}
