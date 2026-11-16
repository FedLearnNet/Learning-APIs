package de.unihamburg.daibetes.api.analysis.run;

import bio.cosy.feddb.core.execution.ExecutionPurpose;
import bio.cosy.feddb.core.execution.event.ExecutionRunsChangedEvent;
import bio.cosy.feddb.core.execution.event.RunFinishedEvent;
import bio.cosy.feddb.core.execution.orchestrator.WorkflowOrchestrator;
import de.unihamburg.daibetes.execution.run.ExecutionRunAO;
import io.quarkus.logging.Log;
import io.quarkus.virtual.threads.VirtualThreads;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.TransactionPhase;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.OnOverflow;

import java.util.concurrent.ExecutorService;

import static de.unihamburg.daibetes.api.analysis.run.DataAnalysisRunServiceImpl.DATA_ANALYSIS_RUN_CHANNEL;

@ApplicationScoped
public class DataAnalysisRunEventsBO {

    @Inject
    ExecutionRunAO runAO;

    @Inject
    DataAnalysisRunResultMapper mapper;

    @Inject
    WorkflowOrchestrator workflowOrchestrator;

    @Inject
    @VirtualThreads
    ExecutorService virtualThreads;

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

    // Finished, failed or stopped, an ended analysis needs none of its containers and volumes anymore: every node
    // uploaded its outputs, and the volumes only passed them to the next node while the run went on.
    void onRunFinished(@Observes(during = TransactionPhase.AFTER_SUCCESS) RunFinishedEvent event) {
        virtualThreads.execute(() -> {
            try {
                if (runAO.isAnalysisTransactional(event.getRunId())) {
                    workflowOrchestrator.cleanup(event.getRunId());
                }
            } catch (RuntimeException e) {
                Log.warnf(e, "Could not clean up the containers of analysis run %d", event.getRunId());
            }
        });
    }
}
