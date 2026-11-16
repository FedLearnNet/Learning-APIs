package bio.cosy.feddb.local.execution.run;

import bio.cosy.feddb.core.execution.event.BaseExecutionRunSender;
import bio.cosy.feddb.core.execution.event.ExecutionRunsChangedEvent;
import bio.cosy.feddb.local.execution.attempt.ExecutionAttemptAO;
import bio.cosy.feddb.local.execution.step.ExecutionRunStepAO;
import io.quarkus.logging.Log;
import io.smallrye.reactive.messaging.annotations.Broadcast;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.OnOverflow;

@ApplicationScoped
public class ExecutionRunSender extends BaseExecutionRunSender<ExecutionRunAO, ExecutionRunStepAO, ExecutionAttemptAO> {

    public static final String RUN_CHANNEL = "execution-runs";

    @Inject
    ExecutionRunMapper mapper;

    @Inject
    @Channel(RUN_CHANNEL)
    @OnOverflow(OnOverflow.Strategy.DROP)
    @Broadcast
    Emitter<ExecutionRunDTO> emitter;

    void onRunsChanged(@Observes ExecutionRunsChangedEvent event) {
        event.getRunIds().forEach(runId -> runAO.findByIdOptional(runId)
                .map(run -> mapper.mapRunEntityToDTO(run, false))
                .ifPresent(run -> {
                    try {
                        emitter.send(run);
                    } catch (RuntimeException e) {
                        Log.debug("Run not sent, nobody follows it", e);
                    }
                }));
    }
}
