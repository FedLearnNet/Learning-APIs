package bio.cosy.feddb.core.execution.file;

import io.quarkus.hibernate.orm.panache.PanacheRepository;

import java.util.List;

public abstract class BaseExecutionFileAO<Entity extends BaseExecutionFileEntity<?, ?>>
        implements PanacheRepository<Entity> {

    public List<Entity> listForAttempt(Long attemptId, ExecutionFileDirection direction) {
        return list("attempt.id = ?1 and direction = ?2 order by id", attemptId, direction);
    }

    public List<Entity> listForRun(Long runId, ExecutionFileDirection direction) {
        return list("attempt.step.run.id = ?1 and direction = ?2 order by id", runId, direction);
    }
}
