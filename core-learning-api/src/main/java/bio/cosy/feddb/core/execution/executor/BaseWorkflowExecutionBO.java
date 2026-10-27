package bio.cosy.feddb.core.execution.executor;

import bio.cosy.feddb.core.api.workflow.node.BaseWorkflowNodeEntity;
import bio.cosy.feddb.core.base.BaseEntity;
import bio.cosy.feddb.core.execution.ExecutionLocation;
import bio.cosy.feddb.core.execution.event.ExecutionEventPublisher;
import bio.cosy.feddb.core.execution.hyperparam.config.HyperParamSearchDTO;
import bio.cosy.feddb.core.execution.run.BaseExecutionRunAO;
import bio.cosy.feddb.core.execution.run.BaseExecutionRunEntity;
import bio.cosy.feddb.core.execution.run.types.RunStatusTypes;
import bio.cosy.feddb.core.execution.step.BaseExecutionRunStepEntity;
import io.quarkus.logging.Log;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.NotFoundException;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Starts an execution: a workflow as one run with a step per node, a single node or a single app as a run
 * of one step. Only creates the run and announces it; the coordinators take it from there.
 */
public abstract class BaseWorkflowExecutionBO<
        Run extends BaseExecutionRunEntity<Step>,
        Step extends BaseExecutionRunStepEntity<Run, Node, ?>,
        Node extends BaseWorkflowNodeEntity,
        RunAO extends BaseExecutionRunAO<Run>
        > {

    @Inject
    ExecutionEventPublisher events;

    @Inject
    public RunAO runAO;

    private final Supplier<Run> newRun;
    private final Supplier<Step> newStep;
    private final ExecutionLocation location;

    protected BaseWorkflowExecutionBO(Supplier<Run> newRun, Supplier<Step> newStep, ExecutionLocation location) {
        this.newRun = newRun;
        this.newStep = newStep;
        this.location = location;
    }

    @Transactional
    public Run executeTransactional(@NotNull @Valid ExecutionStartDTO start, @NotEmpty Collection<Node> nodes) {
        return announceStarted(createTransactional(start, nodes));
    }

    @Transactional
    public Run createTransactional(@NotNull @Valid ExecutionStartDTO start, @NotEmpty Collection<Node> nodes) {
        Set<Long> nodeIds = new HashSet<>();
        nodes.forEach(node -> nodeIds.add(node.getId()));
        requireNodesOf(nodeIds, start.getSearches().keySet(), "Searches");
        requireNodesOf(nodeIds, start.getInputs().keySet(), "Inputs");

        Run run = newRun(start);
        inExecutionOrder(nodes).forEach(node -> newStep(run, node.getNodeId(),
                start.getSearches().get(node.getId()), start.getInputs().get(node.getId()))
                .setWorkflowNode(node));
        runAO.persist(run);
        return run;
    }

    @Transactional
    public Run startTransactional(@NotNull Long runId) {
        Run run = runAO.findByIdWrite(runId).orElseThrow(() -> new NotFoundException("Run " + runId + " not found"));
        if (run.getStatus() != RunStatusTypes.PENDING || run.getStartedAt() != null) {
            throw new IllegalStateException("Run " + runId + " was started already");
        }
        return announceStarted(run);
    }

    /**
     * Runs a single app or model without a workflow node, as a run of one step. The target sets on the
     * step what it runs; hyper parameters come as the search, fixed or searched.
     */
    @Transactional
    public Run executeSingleTransactional(@NotNull @Valid ExecutionStartDTO start, @Valid HyperParamSearchDTO search,
                             LinkedHashMap<String, Object> inputs, @NotNull Consumer<Step> target) {
        requireNodesOf(Set.of(), start.getSearches().keySet(), "Searches");
        requireNodesOf(Set.of(), start.getInputs().keySet(), "Inputs");

        Run run = newRun(start);
        target.accept(newStep(run, start.getName(), search, inputs));
        return persistAndStart(run);
    }

    /**
     * Stops a run: open attempts, steps and the run end as stopped. The launcher stops the containers and
     * revokes the keys of the stopped attempts. A run that already ended is left as it is.
     */
    @Transactional
    public Run stopTransactional(@NotNull Long runId) {
        Run run = runAO.findByIdWrite(runId).orElseThrow(() -> new NotFoundException("Run " + runId + " not found"));
        if (run.hasEnded()) {
            return run;
        }
        run.getSteps().forEach(step -> {
            step.getAttempts().stream()
                    .filter(attempt -> !attempt.hasEnded())
                    .forEach(attempt -> {
                        attempt.end(RunStatusTypes.STOPPED, null);
                        events.attemptFinished(attempt);
                    });
            if (!step.hasEnded()) {
                step.end(RunStatusTypes.STOPPED, null);
            }
        });
        run.end(RunStatusTypes.STOPPED, null);
        events.runFinished(run.getId(), RunStatusTypes.STOPPED);
        Log.infof("Stopped run %d", run.getId());
        return run;
    }

    /** Deletes a run with its steps and attempts, stopping it first if it still runs. */
    @Transactional
    public void deleteTransactional(@NotNull Long runId) {
        Run run = stopTransactional(runId);
        // A step points at its selected attempt, which would block deleting the attempts.
        run.getSteps().forEach(step -> step.setSelectedAttempt(null));
        runAO.flush();
        runAO.delete(run);
        Log.infof("Deleted run %d", runId);
    }

    private void requireNodesOf(Set<Long> nodeIds, Set<Long> keys, String what) {
        if (!nodeIds.containsAll(keys)) {
            throw new IllegalArgumentException(what + " are set for nodes outside the execution: " + keys);
        }
    }

    private Run newRun(ExecutionStartDTO start) {
        Run run = newRun.get();
        run.setPublicId(UUID.randomUUID());
        run.setName(start.getName());
        run.setPurpose(start.getPurpose());
        run.setMode(start.getMode());
        run.setLocation(location);
        run.setStatus(RunStatusTypes.PENDING);
        return run;
    }

    private Step newStep(Run run, String name, HyperParamSearchDTO search, LinkedHashMap<String, Object> inputs) {
        Step step = newStep.get();
        step.setPublicId(UUID.randomUUID());
        step.setRun(run);
        step.setName(name);
        step.setSearch(search);
        step.setInputs(inputs);
        step.setStatus(RunStatusTypes.PENDING);
        run.getSteps().add(step);
        return step;
    }

    // Steps are persisted with the run.
    private Run persistAndStart(Run run) {
        runAO.persist(run);
        return announceStarted(run);
    }

    private Run announceStarted(Run run) {
        events.runStarted(run.getId());
        Log.infof("Started run %d with %d step(s)", run.getId(), run.getSteps().size());
        return run;
    }

    // Same order the workflow coordinator runs them in; nodes without an order last, by id.
    private List<Node> inExecutionOrder(Collection<Node> nodes) {
        return nodes.stream()
                .sorted(Comparator
                        .comparing(Node::getExecutionOrder, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(BaseEntity::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }
}
