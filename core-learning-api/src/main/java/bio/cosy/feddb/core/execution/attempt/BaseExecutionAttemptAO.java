package bio.cosy.feddb.core.execution.attempt;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.persistence.LockModeType;

import java.util.Optional;

public abstract class BaseExecutionAttemptAO<Entity extends BaseExecutionAttemptEntity<?, ?>>
        implements PanacheRepository<Entity> {

    public Optional<Entity> findByIdWrite(Long id) {
        return findByIdOptional(id, LockModeType.PESSIMISTIC_WRITE);
    }
}
