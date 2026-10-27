package de.unihamburg.daibetes.api.development.run;

import bio.cosy.feddb.core.execution.ExecutionMode;
import bio.cosy.feddb.core.execution.event.ExecutionEventPublisher;
import bio.cosy.feddb.core.execution.failure.ExecutionFailure;
import bio.cosy.feddb.core.execution.failure.FailureCategory;
import bio.cosy.feddb.core.execution.run.dto.FinishRunDTO;
import bio.cosy.feddb.core.execution.run.dto.UpdateRunDTO;
import bio.cosy.feddb.core.execution.run.types.RunStatusTypes;
import de.unihamburg.daibetes.execution.attempt.ExecutionAttemptEntity;
import de.unihamburg.daibetes.execution.attempt.ExecutionAttemptLifecycle;
import de.unihamburg.daibetes.execution.run.ExecutionRunAO;
import de.unihamburg.daibetes.execution.step.ExecutionRunStepEntity;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class TestRunCleanupBO {

    private static final String DISCONNECTED = "App engine disconnected during the run";

    @Inject
    ExecutionRunAO runAO;

    @Inject
    ExecutionAttemptLifecycle lifecycle;

    @Inject
    ExecutionEventPublisher events;

    public void finishAttempt(FinishRunDTO finish) {
        lifecycle.finish(finish.getRunId(), finish);
    }

    public void finishFederated(ExecutionRunStepEntity step, FinishRunDTO finish) {
        ExecutionAttemptEntity standing = step.getSelectedAttempt();
        if (standing != null && finish.getMeta() != null) {
            standing.setMeta(finish.getMeta());
        }
        RunStatusTypes status = finish.getError() != null || finish.getStatus() == RunStatusTypes.ERROR
                ? RunStatusTypes.ERROR
                : finish.getStatus() == RunStatusTypes.STOPPED ? RunStatusTypes.STOPPED : RunStatusTypes.FINISHED;
        endFederatedStep(step, status, finish.getError());
    }

    public void endFederatedStep(ExecutionRunStepEntity step, RunStatusTypes status, String error) {
        if (step.hasEnded()) {
            return;
        }
        step.getAttempts().stream()
                .filter(attempt -> !attempt.hasEnded())
                .forEach(attempt -> lifecycle.updateTransactional(attempt.getId(), new UpdateRunDTO(attempt.getId(), status, error)));
        step.end(status, error != null ? new ExecutionFailure(FailureCategory.TOOL, error) : step.getFailure());
        events.stepFinished(step.getId(), status);
        Log.infof("Federated test step %d finished as %s", step.getId(), status);
    }

    public void closeAllOnDisconnect(Long appId) {
        runAO.listOpenTestRunsOfApp(appId).forEach(run -> {
            ExecutionRunStepEntity step = run.getSteps().getFirst();
            if (run.getMode() == ExecutionMode.FEDERATED) {
                endFederatedStep(step, RunStatusTypes.ERROR, DISCONNECTED);
                return;
            }
            step.getAttempts().stream()
                    .filter(attempt -> attempt.getStatus() != RunStatusTypes.PENDING && !attempt.hasEnded())
                    .forEach(attempt -> failAttempt(attempt.getId(), DISCONNECTED));
        });
    }

    public void failAttempt(Long attemptId, String error) {
        lifecycle.updateTransactional(attemptId, new UpdateRunDTO(attemptId, RunStatusTypes.ERROR, error));
    }
}
