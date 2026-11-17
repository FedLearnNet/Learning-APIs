package bio.cosy.feddb.local.execution.step;

import bio.cosy.feddb.core.execution.step.BaseExecutionRunStepAO;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Optional;

@ApplicationScoped
public class ExecutionRunStepAO extends BaseExecutionRunStepAO<ExecutionRunStepEntity> {

    // Runs keep their history when the transformer they ran is deleted.
    public int detachTransformer(Long transformerId) {
        return update("transformer = null where transformer.id = ?1", transformerId);
    }

    public Optional<ExecutionRunStepEntity> findByNodeId(Long runId, String nodeId) {
        return find("run.id = ?1 and workflowNode.nodeId = ?2", runId, nodeId).firstResultOptional();
    }
}
