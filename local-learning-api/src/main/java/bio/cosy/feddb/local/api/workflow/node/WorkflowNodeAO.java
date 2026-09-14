package bio.cosy.feddb.local.api.workflow.node;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

@ApplicationScoped
public class WorkflowNodeAO implements PanacheRepository<WorkflowNodeEntity> {

    public List<WorkflowNodeEntity> findByWorkflowId(Long workflowId) {
        return list("workflow.id", workflowId);
    }

    public void deleteByWorkflowAndNegativeIds(Long workflowId, List<Long> ids) {
        delete("workflow.id = ?1 AND id NOT in ?2", workflowId, ids);
    }
}
