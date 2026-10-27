package bio.cosy.feddb.core.execution.step;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import io.quarkus.panache.common.Page;
import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public abstract class BaseExecutionRunStepAO<Entity extends BaseExecutionRunStepEntity<?, ?, ?>>
        implements PanacheRepository<Entity> {

    public Optional<Entity> findByPublicIdOptional(UUID publicId) {
        return find("publicId", publicId).firstResultOptional();
    }

    public Optional<Entity> findByIdWrite(Long id) {
        return findByIdOptional(id, LockModeType.PESSIMISTIC_WRITE);
    }
}
