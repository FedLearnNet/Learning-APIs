package bio.cosy.feddb.local.execution.queue;

import bio.cosy.feddb.core.execution.ExecutionMode;
import bio.cosy.feddb.core.execution.ExecutionPurpose;
import bio.cosy.feddb.core.execution.event.ExecutionEventPublisher;
import bio.cosy.feddb.local.config.FLNetClientConfig;
import bio.cosy.feddb.local.execution.attempt.ExecutionAttemptAO;
import bio.cosy.feddb.local.execution.attempt.ExecutionAttemptEntity;
import bio.cosy.feddb.local.execution.run.ExecutionRunAO;
import bio.cosy.feddb.local.execution.run.ExecutionRunEntity;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@ApplicationScoped
public class ExecutionQueueBO {

    // federated first, as the other clients wait on them, then TRE, then the rest; older first
    private static final Comparator<ExecutionRunEntity> ORDER = Comparator
            .comparingInt(ExecutionQueueBO::priorityOf)
            .thenComparing(ExecutionRunEntity::getId);

    @Inject
    FLNetClientConfig config;

    @Inject
    ExecutionRunAO runAO;

    @Inject
    ExecutionAttemptAO attemptAO;

    @Inject
    ExecutionEventPublisher publisher;

    public boolean admits(ExecutionAttemptEntity attempt) {
        ExecutionRunEntity run = attempt.getStep().getRun();
        if (!enabled() || run.getPurpose() != ExecutionPurpose.TRAINING) {
            return true;
        }
        List<ExecutionRunEntity> open = runAO.listOpenTrainingsLocked();
        Set<Long> started = startedOf(open);
        if (started.contains(run.getId())) {
            return true;
        }
        List<Long> next = next(open, started).stream().map(ExecutionRunEntity::getId).toList();
        return next.contains(run.getId());
    }

    @Transactional
    public List<QueuedRunDTO> listTransactional() {
        if (!enabled()) {
            return List.of();
        }
        List<ExecutionRunEntity> open = runAO.listOpenTrainings();
        List<ExecutionRunEntity> waiting = waiting(open, startedOf(open));
        List<QueuedRunDTO> queue = new ArrayList<>();
        for (ExecutionRunEntity run : waiting) {
            int position = queue.size() + 1;
            queue.add(new QueuedRunDTO(run.getId(), run.getName(), run.getMode(), position, run.getCreatedAt()));
        }
        return queue;
    }

    @Transactional
    public void launchNextTransactional() {
        if (!enabled()) {
            return;
        }
        List<ExecutionRunEntity> open = runAO.listOpenTrainings();
        Set<Long> started = startedOf(open);
        for (ExecutionRunEntity run : next(open, started)) {
            Log.infof("Launching queued run %d", run.getId());
            attemptAO.listPendingOfRun(run.getId()).forEach(publisher::attemptLaunch);
        }
    }

    private List<ExecutionRunEntity> next(List<ExecutionRunEntity> open, Set<Long> started) {
        long max = config.execution().queue().maxConcurrent();
        long used = open.stream().filter(run -> started.contains(run.getId())).count();
        long free = max - used;
        if (free <= 0) {
            return List.of();
        }
        return waiting(open, started).stream().limit(free).toList();
    }

    private List<ExecutionRunEntity> waiting(List<ExecutionRunEntity> open, Set<Long> started) {
        Set<Long> pending = new HashSet<>(attemptAO.listWaitingRunIds(idsOf(open)));
        return open.stream()
                .filter(run -> pending.contains(run.getId()))
                .filter(run -> !started.contains(run.getId()))
                .sorted(ORDER)
                .toList();
    }

    private Set<Long> startedOf(List<ExecutionRunEntity> open) {
        return new HashSet<>(attemptAO.listStartedRunIds(idsOf(open)));
    }

    private static List<Long> idsOf(List<ExecutionRunEntity> runs) {
        return runs.stream().map(ExecutionRunEntity::getId).toList();
    }

    private static int priorityOf(ExecutionRunEntity run) {
        if (run.getMode() == ExecutionMode.FEDERATED) {
            return 0;
        }
        if (run.getMode() == ExecutionMode.TRE) {
            return 1;
        }
        return 2;
    }

    private boolean enabled() {
        return config.execution().queue().enabled();
    }
}
