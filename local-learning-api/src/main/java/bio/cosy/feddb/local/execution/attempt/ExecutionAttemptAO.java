package bio.cosy.feddb.local.execution.attempt;

import bio.cosy.feddb.core.execution.attempt.BaseExecutionAttemptAO;
import bio.cosy.feddb.core.execution.run.types.RunStatusTypes;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class ExecutionAttemptAO extends BaseExecutionAttemptAO<ExecutionAttemptEntity> {

    public Optional<Long> findLatestIdOfRunOptional(Long runId) {
        return Optional.ofNullable(getEntityManager()
                .createQuery("select max(a.id) from ExecutionAttemptEntity a where a.step.run.id = :runId", Long.class)
                .setParameter("runId", runId)
                .getSingleResult());
    }

    // The run of an attempt of a patient tool; none for any other attempt.
    public Optional<Long> findRunIdOfToolAttemptOptional(Long attemptId) {
        return getEntityManager()
                .createQuery("select a.step.run.id from ExecutionAttemptEntity a where a.id = :attemptId and a.step.run.toolType is not null", Long.class)
                .setParameter("attemptId", attemptId)
                .getResultStream().findFirst();
    }

    // Runs of these with an attempt that is, or was, past pending.
    public List<Long> listStartedRunIds(Collection<Long> runIds) {
        return runIds.isEmpty() ? List.of() : getEntityManager()
                .createQuery("select distinct a.step.run.id from ExecutionAttemptEntity a "
                        + "where a.step.run.id in :runIds and a.status <> :pending", Long.class)
                .setParameter("runIds", runIds)
                .setParameter("pending", RunStatusTypes.PENDING)
                .getResultList();
    }

    // Runs of these with an attempt waiting to launch.
    public List<Long> listWaitingRunIds(Collection<Long> runIds) {
        return runIds.isEmpty() ? List.of() : getEntityManager()
                .createQuery("select distinct a.step.run.id from ExecutionAttemptEntity a "
                        + "where a.step.run.id in :runIds and a.status = :pending", Long.class)
                .setParameter("runIds", runIds)
                .setParameter("pending", RunStatusTypes.PENDING)
                .getResultList();
    }

    public List<ExecutionAttemptEntity> listPendingOfRun(Long runId) {
        return list("step.run.id = ?1 and status = ?2", runId, RunStatusTypes.PENDING);
    }
}
