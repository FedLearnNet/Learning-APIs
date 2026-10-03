package bio.cosy.feddb.core.api.pipeline;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.persistence.LockModeType;
import jakarta.ws.rs.NotFoundException;

public abstract class BasePipelineAO<Entity extends BasePipelineEntity<?>> implements PanacheRepository<Entity> {

    public Entity findByIdAndSecret(Long id, String secret, LockModeType lockMode) {
        return find("id = ?1 and secret = ?2", id, secret).withLock(lockMode).firstResultOptional()
                .orElseThrow(() -> new NotFoundException("Pipeline " + id + " not found"));
    }
}
