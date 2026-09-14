package de.unihamburg.daibetes.api.workflow.connection;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

@ApplicationScoped
public class WorkflowConnectionAO implements PanacheRepository<WorkflowConnectionEntity> {

    public List<WorkflowConnectionEntity> findByWorkflowId(Long workflowId) {
        return list("workflow.id", workflowId);
    }

    public void deleteByWorkflowAndNegativeIds(Long workflowId, List<Long> ids) {
        delete("workflow.id = ?1 AND id NOT in ?2", workflowId, ids);
    }

    public List<WorkflowConnectionEntity> findByWorkflowOutputNodeId(Long workflowNodeId) {
        return list("outputNode.id", workflowNodeId);
    }


}
