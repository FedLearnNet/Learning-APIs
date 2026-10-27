package de.unihamburg.daibetes.api.analysis.run;

import bio.cosy.feddb.core.execution.ExecutionPurpose;
import bio.cosy.feddb.core.execution.event.ExecutionRunsChangedEvent;
import de.unihamburg.daibetes.execution.run.ExecutionRunAO;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.OnOverflow;

import static de.unihamburg.daibetes.api.analysis.run.DataAnalysisRunServiceImpl.DATA_ANALYSIS_RUN_CHANNEL;

@ApplicationScoped
public class DataAnalysisRunEventsBO {

    @Inject
    ExecutionRunAO runAO;

    @Inject
    DataAnalysisRunResultMapper mapper;

    @Inject
    @Channel(DATA_ANALYSIS_RUN_CHANNEL)
    @OnOverflow(OnOverflow.Strategy.DROP)
    Emitter<DataAnalysisRunDTO> runEmitter;

    void onRunsChanged(@Observes ExecutionRunsChangedEvent event) {
        if (event.getPurpose() != ExecutionPurpose.ANALYSIS) {
            return;
        }
        runAO.listByIds(event.getRunIds()).stream()
                .map(run -> mapper.mapRunEntityToResultDTO(run, false))
                .forEach(run -> {
                    try {
                        runEmitter.send(run);
                    } catch (RuntimeException e) {
                        Log.debug("Analysis run not sent, its stream is closed", e);
                    }
                });
    }
}
