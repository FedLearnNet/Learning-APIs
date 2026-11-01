package de.unihamburg.daibetes.api.development.engine;

import bio.cosy.feddb.core.execution.ExecutionPurpose;
import bio.cosy.feddb.core.execution.event.AttemptFinishedEvent;
import bio.cosy.feddb.core.execution.event.AttemptLaunchEvent;
import bio.cosy.feddb.core.execution.event.ExecutionRunsChangedEvent;
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

import java.util.List;
import java.util.concurrent.ExecutorService;

import static de.unihamburg.daibetes.api.development.engine.TestEngineServiceImpl.TEST_ENGINE_CHANNEL;

@ApplicationScoped
public class TestEngineEventsBO {

    @Inject
    TestEngineBO engineBO;

    @Inject
    TestEngineRunBO runBO;

    @Inject
    ExecutionRunAO runAO;

    @Inject
    ExecutionRunDetailMapper runMapper;

    @Inject
    @VirtualThreads
    ExecutorService virtualThreads;

    @Inject
    @Channel(TEST_ENGINE_CHANNEL)
    @OnOverflow(OnOverflow.Strategy.DROP)
    Emitter<ExecutionRunDetailDTO> engineEmitter;

    void onAttemptLaunch(@Observes(during = TransactionPhase.AFTER_SUCCESS) AttemptLaunchEvent event) {
        engineBO.watch(event.getAttemptId());
    }

    void onAttemptFinished(@Observes(during = TransactionPhase.AFTER_SUCCESS) AttemptFinishedEvent event) {
        if (event.getPurpose() == ExecutionPurpose.ENGINE) {
            virtualThreads.execute(() -> runBO.cleanUpTransactional(event.getAttemptId()));
        }
    }

    void onRunsChanged(@Observes ExecutionRunsChangedEvent event) {
        List<Long> runIds = List.copyOf(event.getRunIds());
        if (event.getPurpose() == ExecutionPurpose.ENGINE) {
            runAO.listByIds(runIds).stream()
                    .map(run -> runMapper.mapRunEntityToDetailDTO(run, false))
                    .forEach(run -> {
                        try {
                            engineEmitter.send(run);
                        } catch (RuntimeException e) {
                            Log.debug("Engine run not sent, its stream is closed", e);
                        }
                    });
        } else if (event.getPurpose() == ExecutionPurpose.TEST) {
            // A single run's engine stops once its test run ended
            virtualThreads.execute(() -> runBO.finishSingleRunEnginesTransactional(runIds));
        }
    }
}
