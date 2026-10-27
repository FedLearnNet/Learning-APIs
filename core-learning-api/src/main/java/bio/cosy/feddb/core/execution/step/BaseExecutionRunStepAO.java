package bio.cosy.feddb.core.execution.step;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.persistence.LockModeType;

import java.util.Optional;

public abstract class BaseExecutionRunStepAO<Entity extends BaseExecutionRunStepEntity<?, ?, ?>>
        implements PanacheRepository<Entity> {

    public Optional<Entity> findByIdWrite(Long id) {
        return findByIdOptional(id, LockModeType.PESSIMISTIC_WRITE);
    }
}
