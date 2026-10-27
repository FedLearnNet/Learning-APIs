package bio.cosy.feddb.core.execution.coordinator;

import bio.cosy.feddb.core.execution.BaseExecutionStatusEntity;
import bio.cosy.feddb.core.execution.ExecutionMode;
import bio.cosy.feddb.core.execution.attempt.BaseExecutionAttemptAO;
import bio.cosy.feddb.core.execution.attempt.BaseExecutionAttemptDTO;
import bio.cosy.feddb.core.execution.attempt.BaseExecutionAttemptEntity;
import bio.cosy.feddb.core.execution.attempt.BaseExecutionAttemptMapper;
import bio.cosy.feddb.core.execution.event.ExecutionEventPublisher;
import bio.cosy.feddb.core.execution.event.StepChangedEvent;
import bio.cosy.feddb.core.execution.failure.ExecutionFailure;
import bio.cosy.feddb.core.execution.failure.FailureCategory;
import bio.cosy.feddb.core.execution.helper.NodeHyperParams;
import bio.cosy.feddb.core.execution.hyperparam.config.HyperParamSearchDTO;
import bio.cosy.feddb.core.execution.hyperparam.planner.HyperParamSearchPlanner;
import bio.cosy.feddb.core.execution.hyperparam.planner.SearchPlan;
import bio.cosy.feddb.core.execution.run.types.RunStatusTypes;
import bio.cosy.feddb.core.execution.step.BaseExecutionRunStepAO;
import bio.cosy.feddb.core.execution.step.BaseExecutionRunStepEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.logging.Log;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.ConstraintViolationException;

import java.util.*;
import java.util.function.Supplier;

/**
 * Coordinate one single step
 * Drives a step from the channel: locks it, asks the planner
 * for the next move and applies it - create the next attempt, or finish the step with the best one.
 */
public abstract class BaseStepCoordinator<
        Step extends BaseExecutionRunStepEntity<?, ?, AttemptEntity>,
        AttemptEntity extends BaseExecutionAttemptEntity<Step, ?>,
        AttemptDTO extends BaseExecutionAttemptDTO,
        AttemptMapper extends BaseExecutionAttemptMapper<AttemptDTO, AttemptEntity>,
        AttemptAO extends BaseExecutionAttemptAO<AttemptEntity>,
        StepAO extends BaseExecutionRunStepAO<Step>
        > {

    @Inject
    HyperParamSearchPlanner planner;

    @Inject
    ExecutionEventPublisher events;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    public StepAO stepAO;

    @Inject
    public AttemptAO attemptAO;

    @Inject
    public AttemptMapper attemptMapper;

    private final Supplier<AttemptEntity> newAttempt;

    protected BaseStepCoordinator(Supplier<AttemptEntity> newAttempt) {
        this.newAttempt = newAttempt;
    }

    @Transactional
    public void onStepChangedTransactional(StepChangedEvent event) {
        Optional<Step> locked = stepAO.findByIdWrite(event.getStepId());
        if (locked.isEmpty() || RunStatusTypes.isFinalStatus(locked.get().getStatus())) {
            Log.debugf("Ignoring change of step %d, it is gone or already done", event.getStepId());
            return;
        }
        Step step = locked.get();
        // A federated step runs all its participants at once, the app that runs them drives it.
        if (step.getRun().getMode() == ExecutionMode.FEDERATED) {
            return;
        }

        SearchPlan plan;
        try {
            plan = planner.plan(searchOf(step), step.getAttempts().stream().map(attemptMapper::entityToDto).toList());
        } catch (ConstraintViolationException | IllegalArgumentException e) {
            ExecutionFailure failure = new ExecutionFailure(FailureCategory.VALIDATION, "Search cannot run: " + e.getMessage());
            finishStep(step, RunStatusTypes.ERROR, failure);
            return;
        }

        if (plan.getNext() != null) {
            launchTrial(step, plan.getNext().getTrialIndex(), plan.getNext().getParams());
        } else if (plan.getSelected() != null) {
            step.setSelectedAttempt(attemptOf(step, plan.getSelected()));
            finishStep(step, plan.getStatus(), null);
        } else if (plan.isDone()) {
            finishStep(step, plan.getStatus(), trialFailure(step));
        }
    }

    // A step without a finished trial fails with the cause of its latest failed trial, not only that none finished.
    private ExecutionFailure trialFailure(Step step) {
        Optional<AttemptEntity> failed = step.getAttempts().stream()
                .filter(attempt -> attempt.getFailure() != null && attempt.getFailure().getMessage() != null)
                .max(Comparator.comparing(BaseExecutionStatusEntity::getFinishedAt, Comparator.nullsFirst(Comparator.naturalOrder())));
        if (failed.isEmpty()) {
            return new ExecutionFailure(FailureCategory.TOOL, "No trial of the step finished");
        }
        ExecutionFailure cause = failed.get().getFailure();
        String trials = step.getAttempts().size() == 1 ? "" : "None of " + step.getAttempts().size() + " trials finished; ";
        return new ExecutionFailure(cause.getType(), cause.getCategory() != null ? cause.getCategory() : FailureCategory.TOOL,
                trials + "Trial " + failed.get().getTrialIndex() + " failed: " + cause.getMessage());
    }

    // A step without a search runs its node's hyper parameters, stored as JSON, as a single trial.
    private HyperParamSearchDTO searchOf(Step step) {
        return step.getSearch() != null
                ? step.getSearch()
                : HyperParamSearchDTO.fixed(NodeHyperParams.of(objectMapper, step.getWorkflowNode()));
    }

    /** Launches a trial of the step with the parameters it runs with; its app starts once the launcher has it. */
    public AttemptEntity launchTrial(Step step, int trialIndex, Map<String, Object> params) {
        AttemptEntity attempt = newAttempt.get();
        attempt.setPublicId(UUID.randomUUID());
        attempt.setStep(step);
        attempt.setTrialIndex(trialIndex);
        attempt.setParams(new LinkedHashMap<>(params));
        attempt.setStatus(RunStatusTypes.PENDING);
        step.getAttempts().add(attempt);
        attemptAO.persist(attempt);

        step.begin(RunStatusTypes.RUNNING);
        events.attemptLaunch(attempt.getId());
        Log.infof("Step %d starts trial %d as attempt %d", step.getId(), trialIndex, attempt.getId());
        return attempt;
    }

    private void finishStep(Step step, RunStatusTypes status, ExecutionFailure failure) {
        step.end(status, failure);
        events.stepFinished(step.getId(), status);
        Log.infof("Step %d finished as %s", step.getId(), status);
    }

    // Matched by trial index, which is unique within a step.
    private AttemptEntity attemptOf(Step step, BaseExecutionAttemptDTO selected) {
        List<AttemptEntity> matches = step.getAttempts().stream()
                .filter(attempt -> attempt.getTrialIndex() == selected.getTrialIndex())
                .toList();
        if (matches.size() != 1) {
            throw new IllegalStateException("Step " + step.getId() + " has " + matches.size()
                    + " attempts for trial " + selected.getTrialIndex());
        }
        return matches.getFirst();
    }
}
