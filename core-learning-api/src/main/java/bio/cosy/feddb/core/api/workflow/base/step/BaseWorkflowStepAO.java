package bio.cosy.feddb.core.api.workflow.base.step;

import bio.cosy.feddb.core.api.run.RunStatusTypes;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.transaction.Transactional;

import java.util.Date;
import java.util.Optional;

public abstract class BaseWorkflowStepAO<Entity extends BaseWorkflowStepEntity> implements PanacheRepository<Entity> {

    public Optional<Entity> findByWorkflowNodeId(Long experimentId, String workflowNodeId) {
        return find("experiment.id = ?1 AND workflowNode.nodeId = ?2", experimentId, workflowNodeId).firstResultOptional();
    }

    public Optional<Entity> findByWorkflowNodeId(Long experimentId, Long workflowNodeId) {
        return find("experiment.id = ?1 AND workflowNode.id = ?2", experimentId, workflowNodeId).firstResultOptional();
    }

    public boolean existById(Long id) {
        return find("id", id).firstResultOptional().isPresent();
    }

    @Transactional
    public boolean updateFinishedTransactional(Long stepId, String result) {
        int updated = update(
                """
                        stepStatus = ?1,
                        progress = ?2,
                        updatedAt = ?3,
                        result = ?4
                        where id = ?5
                          and (stepStatus is null or stepStatus not in ?6)
                        """,
                RunStatusTypes.FINISHED,
                100L,
                new Date(),
                result,
                stepId,
                RunStatusTypes.terminalStates()
        );
        if (updated > 1) {
            throw new IllegalStateException("Multiple steps updated for stepId " + stepId);
        }
        return updated == 1;
    }

    @Transactional
    public boolean updateStatusTransactional(Long stepId, RunStatusTypes status) {
        int updated = update(
                """
                        stepStatus = ?1,
                        updatedAt = ?2
                        where id = ?3
                          and (stepStatus is null or stepStatus not in ?4)
                        """,
                status,
                new Date(),
                stepId,
                RunStatusTypes.terminalStates()
        );
        if (updated > 1) {
            throw new IllegalStateException("Multiple steps updated for stepId " + stepId);
        }
        return updated == 1;
    }

    @Transactional
    public boolean updateStatusTransactional(Long stepId, RunStatusTypes status, Float progress) {
        int updated = update(
                """
                        stepStatus = ?1,
                        progress = ?2,
                        updatedAt = ?3
                        where id = ?4
                          and (stepStatus is null or stepStatus not in ?5)
                        """,
                status,
                progress,
                new Date(),
                stepId,
                RunStatusTypes.terminalStates()
        );
        if (updated > 1) {
            throw new IllegalStateException("Multiple steps updated for stepId " + stepId);
        }
        return updated == 1;
    }

    @Transactional
    public boolean updateStatusTransactional(Long stepId, RunStatusTypes status, String containerId) {
        int updated = update(
                """
                        stepStatus = ?1,
                        containerId = ?2,
                        updatedAt = ?3
                        where id = ?4
                          and (stepStatus is null or stepStatus not in ?5)
                        """,
                status,
                containerId,
                new Date(),
                stepId,
                RunStatusTypes.terminalStates()
        );
        if (updated > 1) {
            throw new IllegalStateException("Multiple steps updated for stepId " + stepId);
        }
        return updated == 1;
    }

    @Transactional
    public boolean updateErrorTransactional(Long stepId, String error) {
        int updated = update(
                """
                        stepStatus = ?1,
                        lastError = ?2,
                        updatedAt = ?3
                        where id = ?4
                          and (stepStatus is null or stepStatus not in ?5)
                        """,
                RunStatusTypes.ERROR,
                error,
                new Date(),
                stepId,
                RunStatusTypes.terminalStates()
        );
        if (updated > 1) {
            throw new IllegalStateException("Multiple steps updated for stepId " + stepId);
        }
        return updated == 1;
    }


}
