package bio.cosy.feddb.core.api.workflow.base.experiment;

import bio.cosy.feddb.core.api.project.ProjectStatus;
import bio.cosy.feddb.core.api.workflow.base.step.BaseWorkflowStepEntity;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.transaction.Transactional;

import java.util.Date;

public abstract class BaseWorkflowExperimentAO<Entity extends BaseWorkflowExperimentEntity> implements PanacheRepository<Entity> {


    @Transactional
    public boolean updateStatusTransactional(Long experimentId, ProjectStatus status) {
        int updated = update(
                """
                        experimentStatus = ?1,
                        updatedAt = ?2
                        where id = ?3
                        """,
                status,
                new Date(),
                experimentId
        );
        if (updated > 1) {
            throw new IllegalStateException("Multiple steps updated for stepId " + experimentId);
        }
        return updated == 1;
    }

    @Transactional
    public boolean markExperimentRunning(Long experimentId) {
        int updated = update(
                """
                        experimentStatus = ?1,
                        startedAt = ?2,
                        updatedAt = ?3
                        where id = ?4
                        """,
                ProjectStatus.RUNNING,
                new Date(),
                new Date(),
                experimentId
        );
        return updated == 1;
    }

    @Transactional
    public boolean markExperimentError(Long experimentId) {
        int updated = update(
                """
                        experimentStatus = ?1,
                        updatedAt = ?2,
                        finishedAt = ?3
                        where id = ?4
                        """,
                ProjectStatus.ERROR,
                new Date(),
                new Date(),
                experimentId
        );
        return updated == 1;
    }

    @Transactional
    public boolean markExperimentFinish(Long experimentId) {
        int updated = update(
                """
                        experimentStatus = ?1,
                        updatedAt = ?2,
                        finishedAt = ?3
                        where id = ?4
                        """,
                ProjectStatus.FINISHED,
                new Date(),
                new Date(),
                experimentId
        );
        return updated == 1;
    }

    @Transactional
    public boolean markExperimentStop(Long experimentId) {
        int updated = update(
                """
                        experimentStatus = ?1,
                        updatedAt = ?2,
                        finishedAt = ?3
                        where id = ?4
                        """,
                ProjectStatus.STOPPED,
                new Date(),
                new Date(),
                experimentId
        );
        return updated == 1;
    }

    @Transactional
    public <T extends BaseWorkflowStepEntity<?, ?>> boolean setCurrentWorkflowNode(Long experimentId, T currentWorkflowNode) {
        int updated = update(
                """
                        currentWorkflowNode = ?1,
                        updatedAt = ?2
                        where id = ?3
                        """,
                currentWorkflowNode,
                new Date(),
                experimentId
        );
        return updated == 1;
    }

}
