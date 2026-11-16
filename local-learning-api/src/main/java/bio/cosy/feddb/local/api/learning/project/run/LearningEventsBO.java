package bio.cosy.feddb.local.api.learning.project.run;

import bio.cosy.feddb.core.execution.ExecutionMode;
import bio.cosy.feddb.core.execution.ExecutionPurpose;
import bio.cosy.feddb.core.execution.event.AttemptFinishedEvent;
import bio.cosy.feddb.core.execution.run.types.RunStatusTypes;
import bio.cosy.feddb.local.execution.run.ExecutionRunDTO;
import bio.cosy.feddb.local.execution.run.ExecutionRunSender;
import io.quarkus.logging.Log;
import io.quarkus.runtime.StartupEvent;
import io.quarkus.virtual.threads.VirtualThreads;
import io.smallrye.mutiny.Multi;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.TransactionPhase;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Channel;

import java.util.concurrent.ExecutorService;

@ApplicationScoped
public class LearningEventsBO {

    @Inject
    LearningRunBO runBO;

    @Inject
    @VirtualThreads
    ExecutorService virtualThreads;

    @Inject
    @Channel(ExecutionRunSender.RUN_CHANNEL)
    Multi<ExecutionRunDTO> runChanges;

    void reportAttempts(@Observes StartupEvent event) {
        runChanges.select().where(run -> run.getPurpose() == ExecutionPurpose.TRAINING &&
                        (run.getMode() == ExecutionMode.FEDERATED || run.getMode() == ExecutionMode.TRE))
                .onItem().transformToIterable(run -> run.getSteps().stream()
                        .flatMap(step -> step.getAttempts().stream())
                        .map(attempt -> new AttemptReport(attempt.getId(), attempt.getStatus()))
                        .toList())
                .select().distinct(attempt -> attempt.id() + ":" + attempt.status())
                .emitOn(virtualThreads)
                .subscribe().with(attempt -> report(attempt.id(), attempt.status()),
                        failure -> Log.errorf(failure, "Attempts of trainings are no longer reported to the platform"));
        virtualThreads.execute(() -> runBO.listUnhandledEndsTransactional().forEach(this::report));
    }

    void onAttemptFinished(@Observes(during = TransactionPhase.AFTER_SUCCESS) AttemptFinishedEvent event) {
        if (event.getPurpose() == ExecutionPurpose.TRAINING) {
            virtualThreads.execute(() -> report(event.getAttemptId(), event.getStatus()));
        }
    }

    private void report(Long attemptId, RunStatusTypes status) {
        try {
            runBO.reportStatusTransactional(attemptId, status);
        } catch (RuntimeException e) {
            Log.errorf(e, "Could not report attempt %d to the platform", attemptId);
        }
    }

    private record AttemptReport(Long id, RunStatusTypes status) {
    }
}
