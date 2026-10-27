package bio.cosy.feddb.core.execution.run;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.persistence.LockModeType;

import java.util.Optional;

public abstract class BaseExecutionRunAO<R extends BaseExecutionRunEntity<?>> implements PanacheRepository<R> {

    public Optional<R> findByIdWrite(Long id) {
        return findByIdOptional(id, LockModeType.PESSIMISTIC_WRITE);
    }
}
