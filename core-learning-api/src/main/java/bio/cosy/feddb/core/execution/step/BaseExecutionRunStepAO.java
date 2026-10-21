package bio.cosy.feddb.core.execution.step;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import io.quarkus.panache.common.Page;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public abstract class BaseExecutionRunStepAO<Entity extends BaseExecutionRunStepEntity<?, ?, ?>>
        implements PanacheRepository<Entity> {

    public Optional<Entity> findByPublicIdOptional(UUID publicId) {
        return find("publicId", publicId).firstResultOptional();
    }

    public Optional<Entity> findByWorkflowNodeId(Long runId, Long workflowNodeId) {
        return find("run.id = ?1 and workflowNode.id = ?2", runId, workflowNodeId).firstResultOptional();
    }

    public List<Entity> listByRun(Long runId, Page page) {
        return find("run.id = ?1 order by id", runId).page(page).list();
    }
}
