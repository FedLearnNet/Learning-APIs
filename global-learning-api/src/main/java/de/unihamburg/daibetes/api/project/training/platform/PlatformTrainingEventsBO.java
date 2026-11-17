package de.unihamburg.daibetes.api.project.training.platform;

import bio.cosy.feddb.core.execution.ExecutionPurpose;
import bio.cosy.feddb.core.execution.event.ExecutionRunsChangedEvent;
import bio.cosy.feddb.core.execution.event.StepFinishedEvent;
import bio.cosy.feddb.core.execution.run.types.RunStatusTypes;
import de.unihamburg.daibetes.execution.run.ExecutionRunAO;
import de.unihamburg.daibetes.execution.run.ExecutionRunDetailDTO;
import de.unihamburg.daibetes.execution.run.ExecutionRunDetailMapper;
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

import static de.unihamburg.daibetes.api.project.training.ProjectExperimentServiceImpl.EXPERIMENT_CHANNEL;

@ApplicationScoped
public class PlatformTrainingEventsBO {

    @Inject
    PlatformTrainingBO platformTrainingBO;

    @Inject
    ExecutionRunAO runAO;

    @Inject
    ExecutionRunDetailMapper mapper;

    @Inject
    @Channel(EXPERIMENT_CHANNEL)
    @OnOverflow(OnOverflow.Strategy.DROP)
    Emitter<ExecutionRunDetailDTO> runEmitter;

    @Inject
    @VirtualThreads
    ExecutorService virtualThreads;

    // Keeping what the step wrote needs a transaction of its own, which cannot begin on the committing thread.
    void onStepFinished(@Observes(during = TransactionPhase.AFTER_SUCCESS) StepFinishedEvent event) {
        if (event.getStatus() == RunStatusTypes.FINISHED) {
            virtualThreads.execute(() -> platformTrainingBO.keepOutputsTransactional(event.getStepId()));
        }
    }

    // Changed trainings, also those across clinics, go to the streams that follow them; a closed stream only misses them.
    void onRunsChanged(@Observes ExecutionRunsChangedEvent event) {
        if (event.getPurpose() != ExecutionPurpose.TRAINING) {
            return;
        }
        runAO.listByIds(event.getRunIds()).stream()
                .filter(run -> run.project().isPresent())
                .map(run -> mapper.mapRunEntityToDetailDTO(run, false))
                .forEach(run -> {
                    try {
                        runEmitter.send(run);
                    } catch (RuntimeException e) {
                        Log.debug("Training not sent, its stream is closed", e);
                    }
                });
    }
}
