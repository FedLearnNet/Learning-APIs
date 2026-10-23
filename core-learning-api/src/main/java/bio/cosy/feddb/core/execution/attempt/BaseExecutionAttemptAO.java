package bio.cosy.feddb.core.execution.attempt;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import io.quarkus.panache.common.Page;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public abstract class BaseExecutionAttemptAO<Entity extends BaseExecutionAttemptEntity<?, ?>>
        implements PanacheRepository<Entity> {

    public Optional<Entity> findByPublicIdOptional(UUID publicId) {
        return find("publicId", publicId).firstResultOptional();
    }

    public Optional<Entity> findLatestByStep(Long stepId) {
        return find("step.id = ?1 order by trialIndex desc", stepId).firstResultOptional();
    }

    public List<Entity> listByStep(Long stepId, Page page) {
        return find("step.id = ?1 order by trialIndex", stepId).page(page).list();
    }
}
