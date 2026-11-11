package de.unihamburg.daibetes.api.project.training.federated;

import bio.cosy.feddb.core.api.project.ProjectStatus;
import bio.cosy.feddb.core.api.socket.LearningClientSyncResponseDTO;
import bio.cosy.feddb.core.execution.failure.ExecutionFailure;
import bio.cosy.feddb.core.execution.failure.FailureCategory;
import bio.cosy.feddb.core.execution.run.types.RunStatusTypes;
import de.unihamburg.daibetes.execution.attempt.ExecutionAttemptEntity;
import de.unihamburg.daibetes.execution.federation.ExecutionFederationEntity;
import de.unihamburg.daibetes.execution.federation.ExecutionParticipantEntity;
import de.unihamburg.daibetes.execution.run.ExecutionRunEntity;
import de.unihamburg.daibetes.execution.step.ExecutionRunStepEntity;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class FederatedTrainingProgressBO {

    public boolean applyReport(ExecutionParticipantEntity participant, LearningClientSyncResponseDTO update) {
        String clinicId = participant.getParticipantId();
        ProjectStatus current = participant.getStepStatus();
        if (ProjectStatus.isFinalStatus(current)) {
            Log.infof("Ignoring update of clinic %s, its step already ended as %s", clinicId, current);
            return false;
        }
        ProjectStatus reported = update.getStepStatus();
        if (update.getError() != null) {
            Log.errorf("Clinic %s reported an error: %s", clinicId, update.getError());
            // Older clients only send the error text.
            reported = reported != null ? reported : ProjectStatus.ERROR;
        }
        if (reported == null) {
            Log.warnf("Ignoring update of clinic %s without status", clinicId);
            return false;
        }
        String nodeId = participant.getCurrentNodeId();
        if (nodeId != null && update.getCurrentNodeId() == null && ProjectStatus.isPreRunning(reported)) {
            Log.warnf("Ignoring stale update of clinic %s without node, it is at node %s", clinicId, nodeId);
            return false;
        }
        if (nodeId != null && update.getCurrentNodeId() != null && !nodeId.equals(update.getCurrentNodeId())) {
            Log.warnf("Ignoring update of clinic %s for node %s, it is at node %s", clinicId, update.getCurrentNodeId(), nodeId);
            return false;
        }
        if (reported == current) {
            return current == ProjectStatus.RUNNING;
        }
        if (current == ProjectStatus.RUNNING && reported == ProjectStatus.READY) {
            Log.warnf("Ignoring update of clinic %s from RUNNING back to READY", clinicId);
            return true;
        }
        Log.infof("Clinic %s moved from %s to %s", clinicId, current, reported);
        participant.setStatus(update.getProjectStatus());
        participant.setCurrentNodeId(update.getCurrentNodeId());
        participant.setStepStatus(reported);
        reportOnAttempt(participant, reported, update.getError());
        return true;
    }

    private void reportOnAttempt(ExecutionParticipantEntity participant, ProjectStatus reported, String error) {
        Optional<ExecutionRunStepEntity> step = participant.getRun().getCurrentStep();
        Optional<ExecutionAttemptEntity> found = step.flatMap(current -> current.getAttempts().stream()
                .filter(attempt -> attempt.getFederation() != null && attempt.getFederation().getParticipant() == participant)
                .findFirst());
        if (found.isEmpty() || found.get().hasEnded()) {
            return;
        }
        ExecutionAttemptEntity attempt = found.get();
        ExecutionFederationEntity federation = step.get().getFederation();
        switch (reported) {
            case RUNNING -> attempt.moveTo(RunStatusTypes.RUNNING);
            case FINISHED -> {
                if (federation != null && federation.getTotalRounds() != null) {
                    attempt.getFederation().setCurrentRound(federation.getTotalRounds());
                }
                attempt.end(RunStatusTypes.FINISHED, null);
            }
            case ERROR -> attempt.end(RunStatusTypes.ERROR, new ExecutionFailure(FailureCategory.TOOL,
                    "Clinic " + participant.getParticipantId() + ": " + (error != null ? error : "reported an error")));
            case STOPPED, SHUTDOWN -> attempt.end(RunStatusTypes.STOPPED,
                    new ExecutionFailure(FailureCategory.CANCELLED, "Clinic " + participant.getParticipantId() + " stopped"));
            default -> {
            }
        }
        if (federation != null) {
            // The round the clinics are at; the platform's aggregator does not report one.
            step.get().getAttempts().stream()
                    .filter(each -> each.getFederation() != null && each.getFederation().getParticipant() != null
                            && each.getFederation().getCurrentRound() != null)
                    .mapToInt(each -> each.getFederation().getCurrentRound())
                    .min()
                    .ifPresent(federation::setCurrentRound);
        }
    }

    public Optional<ExecutionRunStepEntity> stepToStart(ExecutionRunEntity run) {
        Optional<ExecutionRunStepEntity> current = run.getCurrentStep();
        return current.filter(step -> step.getStatus() == RunStatusTypes.INITIALIZED)
                .or(() -> current.isEmpty() ? run.getStepsInExecutionOrder().stream().findFirst() : Optional.empty());
    }

    public void markRunning(ExecutionRunStepEntity step) {
        step.moveTo(RunStatusTypes.RUNNING);
    }

    public Optional<ExecutionRunStepEntity> finishStep(ExecutionRunEntity run, ExecutionRunStepEntity step) {
        step.moveTo(RunStatusTypes.FINISHED);
        List<ExecutionRunStepEntity> steps = run.getStepsInExecutionOrder();
        int index = steps.indexOf(step);
        Log.infof("All clinics finished step %d of training %d", step.getId(), run.getId());
        return index + 1 < steps.size() ? Optional.of(steps.get(index + 1)) : Optional.empty();
    }
}
