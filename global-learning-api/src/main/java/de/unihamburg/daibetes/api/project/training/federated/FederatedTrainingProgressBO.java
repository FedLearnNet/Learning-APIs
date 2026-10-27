package de.unihamburg.daibetes.api.project.training.federated;

import bio.cosy.feddb.core.api.project.ProjectStatus;
import bio.cosy.feddb.core.api.socket.LearningClientSyncResponseDTO;
import bio.cosy.feddb.core.execution.run.types.RunStatusTypes;
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
        return true;
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
