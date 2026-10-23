package bio.cosy.feddb.core.execution.run;

import bio.cosy.feddb.core.execution.ExecutionLocation;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import io.quarkus.panache.common.Page;
import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public abstract class BaseExecutionRunAO<R extends BaseExecutionRunEntity<?>> implements PanacheRepository<R> {


    public Optional<R> findByPublicIdOptional(UUID publicId) {
        return find("publicId", publicId).firstResultOptional();
    }

    public Optional<R> findByIdWrite(Long id) {
        return findByIdOptional(id, LockModeType.PESSIMISTIC_WRITE);
    }

    public List<R> listByLocation(ExecutionLocation location, Page page) {
        return find("location = ?1 order by id desc", location)
                .page(page).list();
    }
}
