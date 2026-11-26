package bio.cosy.feddb.core.execution.run;

import bio.cosy.feddb.core.execution.run.types.RunStatusTypes;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.persistence.LockModeType;

import java.util.Optional;

public abstract class BaseExecutionRunAO<R extends BaseExecutionRunEntity<?>> implements PanacheRepository<R> {

    public long countOpen() {
        return count("status not in ?1", RunStatusTypes.terminalStates());
    }

    public Optional<R> findByIdWrite(Long id) {
        return findByIdOptional(id, LockModeType.PESSIMISTIC_WRITE);
    }
}
