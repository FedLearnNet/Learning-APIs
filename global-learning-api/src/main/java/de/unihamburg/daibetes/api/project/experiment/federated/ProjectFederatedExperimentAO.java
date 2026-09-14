package de.unihamburg.daibetes.api.project.experiment.federated;

import bio.cosy.feddb.core.api.project.ProjectStatus;
import bio.cosy.feddb.core.api.workflow.base.experiment.BaseWorkflowExperimentAO;
import de.unihamburg.daibetes.api.project.experiment.federated.participants.ProjectFederatedExperimentParticipantEntity;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.LockModeType;
import jakarta.transaction.Transactional;

import java.util.Date;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class ProjectFederatedExperimentAO extends BaseWorkflowExperimentAO<ProjectFederatedExperimentEntity> {

    public List<ProjectFederatedExperimentEntity> findByProjectId(Long id) {
        return list("project.id = ?1",
                Sort.by("createdAt", Sort.Direction.Descending),
                id);
    }

    public Optional<ProjectFederatedExperimentEntity> findByGlobalUniqueID(String id) {
        return find("globalUniqueId = ?1", id).firstResultOptional();
    }

    public Optional<ProjectFederatedExperimentEntity> findByGlobalUniqueID(String id, LockModeType lockMode) {
        return find("globalUniqueId = ?1", id).withLock(lockMode).firstResultOptional();
    }

    public Optional<ProjectFederatedExperimentEntity> findByIdOptional(Long id, LockModeType lockMode) {
        return find("id", id).withLock(lockMode).firstResultOptional();
    }

    @Transactional
    public boolean updateAcceptanceTransactional(Long experimentId,
                                                 Long acceptanceCount,
                                                 Long acceptanceClinicCount,
                                                 Boolean modelCanBePublic,
                                                 ProjectStatus experimentStatus) {
        int updated = update(
                """
                        acceptanceCount = ?1,
                        acceptanceClinicCount = ?2,
                        modelCanBePublic = ?3,
                        experimentStatus = ?4,
                        updatedAt = ?5
                        where id = ?6
                        """,
                acceptanceCount,
                acceptanceClinicCount,
                modelCanBePublic,
                experimentStatus,
                new Date(),
                experimentId
        );
        if (updated > 1) {
            throw new IllegalStateException("Multiple experiments updated for experimentId " + experimentId);
        }
        return updated == 1;
    }

    @Transactional
    public boolean startLearningTransactional(Long experimentId, ProjectFederatedExperimentParticipantEntity coordinator) {
        Date now = new Date();
        int updated = update(
                """
                        experimentStatus = ?1,
                        startedAt = ?2,
                        coordinator = ?3,
                        updatedAt = ?4
                        where id = ?5
                        """,
                ProjectStatus.RUNNING,
                now,
                coordinator,
                now,
                experimentId
        );
        if (updated > 1) {
            throw new IllegalStateException("Multiple experiments updated for experimentId " + experimentId);
        }
        return updated == 1;
    }
}
