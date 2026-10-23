package bio.cosy.feddb.core.execution.coordinator;

import bio.cosy.feddb.core.execution.attempt.BaseExecutionAttemptAO;
import bio.cosy.feddb.core.execution.attempt.BaseExecutionAttemptDTO;
import bio.cosy.feddb.core.execution.attempt.BaseExecutionAttemptEntity;
import bio.cosy.feddb.core.execution.attempt.BaseExecutionAttemptMapper;
import bio.cosy.feddb.core.execution.event.ExecutionEventPublisher;
import bio.cosy.feddb.core.execution.event.StepChangedEvent;
import bio.cosy.feddb.core.execution.failure.ExecutionFailure;
import bio.cosy.feddb.core.execution.failure.FailureCategory;
import bio.cosy.feddb.core.execution.hyperparam.config.HyperParamSearchDTO;
import bio.cosy.feddb.core.execution.hyperparam.planner.HyperParamSearchPlanner;
import bio.cosy.feddb.core.execution.hyperparam.planner.SearchPlan;
import bio.cosy.feddb.core.execution.hyperparam.strategy.Candidate;
import bio.cosy.feddb.core.execution.run.types.RunStatusTypes;
import bio.cosy.feddb.core.execution.step.BaseExecutionRunStepAO;
import bio.cosy.feddb.core.execution.step.BaseExecutionRunStepEntity;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.logging.Log;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.ConstraintViolationException;

import java.time.Instant;
import java.util.*;

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

    protected abstract AttemptEntity newAttempt();

    @Transactional
    public void onStepChanged(StepChangedEvent event) {
        Optional<Step> locked = stepAO.findByIdWrite(event.getStepId());
        if (locked.isEmpty() || RunStatusTypes.isFinalStatus(locked.get().getStatus())) {
            Log.debugf("Ignoring change of step %d, it is gone or already done", event.getStepId());
            return;
        }
        Step step = locked.get();

        SearchPlan plan;
        try {
            plan = planner.plan(searchOf(step), step.getAttempts().stream().map(attemptMapper::entityToDto).toList());
        } catch (ConstraintViolationException | IllegalArgumentException e) {
            ExecutionFailure failure = new ExecutionFailure(FailureCategory.VALIDATION, "Search cannot run: " + e.getMessage());
            finish(step, RunStatusTypes.ERROR, failure);
            return;
        }

        if (plan.getNext() != null) {
            start(step, plan.getNext());
        } else if (plan.getSelected() != null) {
            step.setSelectedAttempt(attemptOf(step, plan.getSelected()));
            finish(step, plan.getStatus(), null);
        } else if (plan.isDone()) {
            ExecutionFailure failure = new ExecutionFailure(FailureCategory.TOOL, "No trial of the step finished");
            finish(step, plan.getStatus(), failure);
        }
    }

    // A step without a search runs its node's hyper parameters, stored as JSON, as a single trial.
    private HyperParamSearchDTO searchOf(Step step) {
        if (step.getSearch() != null) {
            return step.getSearch();
        }
        String hyperParams = step.getWorkflowNode().getHyperParams();
        if (hyperParams == null || hyperParams.isBlank()) {
            return HyperParamSearchDTO.fixed(Map.of());
        }
        try {
            return HyperParamSearchDTO.fixed(objectMapper.readValue(hyperParams,
                    objectMapper.getTypeFactory().constructMapType(LinkedHashMap.class, String.class, Object.class)));
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Hyper parameters of the step's node are not valid JSON", e);
        }
    }

    private void start(Step step, Candidate trial) {
        AttemptEntity attempt = newAttempt();
        attempt.setPublicId(UUID.randomUUID());
        attempt.setStep(step);
        attempt.setTrialIndex(trial.getTrialIndex());
        attempt.setParams(new LinkedHashMap<>(trial.getParams()));
        attempt.setStatus(RunStatusTypes.PENDING);
        step.getAttempts().add(attempt);
        attemptAO.persist(attempt);

        if (step.getStartedAt() == null) {
            step.setStartedAt(Instant.now());
        }
        step.setStatus(RunStatusTypes.RUNNING);
        events.attemptLaunch(attempt.getId());
        Log.infof("Step %d starts trial %d as attempt %d", step.getId(), trial.getTrialIndex(), attempt.getId());
    }

    private void finish(Step step, RunStatusTypes status, ExecutionFailure failure) {
        step.setStatus(status);
        step.setFinishedAt(Instant.now());
        step.setFailure(failure);
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
