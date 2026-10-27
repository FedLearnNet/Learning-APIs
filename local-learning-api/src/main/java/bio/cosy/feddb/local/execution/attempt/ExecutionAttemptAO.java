package bio.cosy.feddb.local.execution.attempt;

import bio.cosy.feddb.core.execution.attempt.BaseExecutionAttemptAO;
import jakarta.enterprise.context.ApplicationScoped;

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
}
