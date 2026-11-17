package de.unihamburg.daibetes.api.project.training.federated;

import bio.cosy.feddb.core.api.project.ProjectStatus;
import bio.cosy.feddb.core.execution.event.ExecutionEventPublisher;
import bio.cosy.feddb.core.execution.failure.ExecutionFailure;
import bio.cosy.feddb.core.execution.failure.FailureCategory;
import bio.cosy.feddb.core.execution.run.types.RunStatusTypes;
import bio.cosy.feddb.core.services.controller.RelayStopRequestDTO;
import de.unihamburg.daibetes.api.feddbclient.FLNetClientBroadcastBO;
import de.unihamburg.daibetes.execution.attempt.ExecutionAttemptEntity;
import de.unihamburg.daibetes.execution.experiment.ExecutionExperimentEntity;
import de.unihamburg.daibetes.execution.federation.ExecutionFederationEntity;
import de.unihamburg.daibetes.execution.run.ExecutionRunEntity;
import de.unihamburg.daibetes.execution.step.ExecutionRunStepEntity;
import de.unihamburg.daibetes.services.GlobalRelayService;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.util.Objects;
import java.util.Optional;

@ApplicationScoped
public class FederatedTrainingCleanupBO {

    @Inject
    FLNetClientBroadcastBO broadcastBO;

    @Inject
    ExecutionEventPublisher events;

    @Inject
    @RestClient
    GlobalRelayService globalRelayService;

    public void finishTraining(ExecutionExperimentEntity experiment, ExecutionRunEntity run) {
        run.getParticipants().forEach(participant -> {
            participant.setStatus(RunStatusTypes.FINISHED);
            participant.setStepStatus(ProjectStatus.FINISHED);
        });
        experiment.setStatus(ProjectStatus.FINISHED);
        finishRun(run, RunStatusTypes.FINISHED, null);
    }

    public void stopTraining(ExecutionExperimentEntity experiment, ExecutionRunEntity run, ProjectStatus reason) {
        Log.infof("Stopping training %d as %s", run.getId(), reason);
        RunStatusTypes status = reason == ProjectStatus.ERROR ? RunStatusTypes.ERROR : RunStatusTypes.STOPPED;
        ExecutionFailure stopped = new ExecutionFailure(FailureCategory.CANCELLED, "Training stopped as " + reason.name());
        run.getSteps().forEach(step -> {
            stopRelay(step.getFederation());
            if (!step.hasEnded() && step.getStatus() != RunStatusTypes.PENDING) {
                ExecutionFailure cause = failureOf(step).orElse(status == RunStatusTypes.ERROR ? null : stopped);
                step.getAttempts().stream().filter(attempt -> !attempt.hasEnded()).forEach(attempt -> {
                    attempt.end(RunStatusTypes.STOPPED, stopped);
                    // The platform's aggregator runs in a container of its own, stopped with its attempt.
                    if (FederatedTrainingAggregatorBO.isAggregator(attempt)) {
                        events.attemptFinished(attempt);
                    }
                });
                step.end(status, cause);
            }
        });
        run.getParticipants().forEach(participant -> {
            participant.setStatus(RunStatusTypes.STOPPED);
            participant.setStepStatus(reason);
        });
        experiment.setStatus(reason);
        if (!run.hasEnded()) {
            finishRun(run, status, run.getSteps().stream().map(ExecutionRunStepEntity::getFailure).filter(Objects::nonNull).findFirst().orElse(null));
        }
        String globalUniqueId = experiment.getGlobalUniqueId();
        broadcastBO.sendAfterCommit("Stop of training " + run.getId(), () -> broadcastBO.stopLearning(globalUniqueId));
    }

    public void stopRelay(ExecutionFederationEntity federation) {
        if (federation == null || federation.getConfig() == null || federation.getConfig().getChannel() == null
                || Boolean.TRUE.equals(federation.getRelayStopped())) {
            return;
        }
        Long stepId = federation.getStep().getId();
        try (Response response = globalRelayService.stopFL(new RelayStopRequestDTO(federation.getConfig().getChannel()))) {
            if (response.getStatus() != Response.Status.OK.getStatusCode()) {
                Log.warnf("Relay server answered %d when stopping the relay run of step %d", response.getStatus(), stepId);
                return;
            }
            federation.setRelayStopped(true);
            Log.infof("Stopped relay run of step %d", stepId);
        } catch (RuntimeException e) {
            Log.warnf(e, "Could not stop the relay run of step %d", stepId);
        }
    }

    // The first failure a clinic reported in the step.
    private Optional<ExecutionFailure> failureOf(ExecutionRunStepEntity step) {
        return step.getAttempts().stream().map(ExecutionAttemptEntity::getFailure)
                .filter(failure -> failure != null && failure.getCategory() != FailureCategory.CANCELLED)
                .findFirst();
    }

    private void finishRun(ExecutionRunEntity run, RunStatusTypes status, ExecutionFailure failure) {
        run.end(status, failure);
        events.runFinished(run.getId(), status);
    }
}
