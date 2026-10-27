package bio.cosy.feddb.local.api.learning.project.run;

import bio.cosy.feddb.core.execution.ExecutionMode;
import bio.cosy.feddb.core.execution.ExecutionPurpose;
import bio.cosy.feddb.core.execution.run.types.RunStatusTypes;
import bio.cosy.feddb.local.execution.run.ExecutionRunDTO;
import bio.cosy.feddb.local.execution.run.ExecutionRunSender;
import io.quarkus.logging.Log;
import io.quarkus.runtime.StartupEvent;
import io.quarkus.virtual.threads.VirtualThreads;
import io.smallrye.mutiny.Multi;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Channel;

import java.util.concurrent.ExecutorService;

@ApplicationScoped
public class FederatedLearningEventsBO {

    @Inject
    FederatedLearningRunBO runBO;

    @Inject
    @VirtualThreads
    ExecutorService virtualThreads;

    @Inject
    @Channel(ExecutionRunSender.RUN_CHANNEL)
    Multi<ExecutionRunDTO> runChanges;

    void reportAttempts(@Observes StartupEvent event) {
        runChanges.select().where(run -> run.getPurpose() == ExecutionPurpose.TRAINING && run.getMode() == ExecutionMode.FEDERATED)
                .onItem().transformToIterable(run -> run.getSteps().stream().flatMap(step -> step.getAttempts().stream()).toList())
                .select().distinct(attempt -> attempt.getId() + ":" + attempt.getStatus())
                .emitOn(virtualThreads)
                .subscribe().with(attempt -> report(attempt.getId(), attempt.getStatus()),
                        failure -> Log.errorf(failure, "Attempts of trainings are no longer reported to the platform"));
    }

    private void report(Long attemptId, RunStatusTypes status) {
        try {
            runBO.reportStatusTransactional(attemptId, status);
        } catch (RuntimeException e) {
            Log.errorf(e, "Could not report attempt %d to the platform", attemptId);
        }
    }
}
