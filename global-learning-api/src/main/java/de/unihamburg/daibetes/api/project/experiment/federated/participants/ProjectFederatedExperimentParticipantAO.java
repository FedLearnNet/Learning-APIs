package de.unihamburg.daibetes.api.project.experiment.federated.participants;

import bio.cosy.feddb.core.api.project.ProjectStatus;
import bio.cosy.feddb.core.api.run.RunStatusTypes;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.LockModeType;
import jakarta.transaction.Transactional;

import java.util.Date;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class ProjectFederatedExperimentParticipantAO implements PanacheRepository<ProjectFederatedExperimentParticipantEntity> {

    /**
     * Finds all participant entities by experiment ID.
     *
     * @param experimentId the ID of the experiment
     * @return a list of ProjectFederatedExperimentParticipantEntity
     */
    public List<ProjectFederatedExperimentParticipantEntity> getAllByExperiment(Long experimentId) {
        return find("experiment.id", experimentId).list();
    }

    /**
     * Finds a participant entity by experiment ID and clinic ID.
     *
     * @param globalUniqueId the ID of the experiment
     * @param clinicId     the unique random clinic ID
     * @return an Optional containing the found ProjectFederatedExperimentParticipantEntity, or an empty Optional if not found
     */
    public Optional<ProjectFederatedExperimentParticipantEntity> findByExperimentIdAndClinicId(String globalUniqueId, String clinicId) {
        return find("experiment.globalUniqueId = ?1 and uniqueRandomClinicId = ?2", globalUniqueId, clinicId).firstResultOptional();
    }

    public Optional<ProjectFederatedExperimentParticipantEntity> findByExperimentIdAndClinicId(String globalUniqueId,
                                                                                               String clinicId,
                                                                                               LockModeType lockMode) {
        return find("experiment.globalUniqueId = ?1 and uniqueRandomClinicId = ?2", globalUniqueId, clinicId)
                .withLock(lockMode)
                .firstResultOptional();
    }

    @Transactional
    public boolean updateStatusTransactional(Long participantId,
                                             RunStatusTypes projectStatus,
                                             String currentNodeId,
                                             ProjectStatus stepStatus) {
        int updated = update(
                """
                        projectStatus = ?1,
                        currentNodeId = ?2,
                        stepStatus = ?3,
                        updatedAt = ?4
                        where id = ?5
                        """,
                projectStatus,
                currentNodeId,
                stepStatus,
                new Date(),
                participantId
        );
        if (updated > 1) {
            throw new IllegalStateException("Multiple participants updated for participantId " + participantId);
        }
        return updated == 1;
    }

    @Transactional
    public int updateStepStatusForExperimentTransactional(Long experimentId, String currentNodeId, ProjectStatus stepStatus) {
        return update(
                """
                        currentNodeId = ?1,
                        stepStatus = ?2,
                        updatedAt = ?3
                        where experiment.id = ?4
                        """,
                currentNodeId,
                stepStatus,
                new Date(),
                experimentId
        );
    }

    @Transactional
    public int updateProjectAndStepStatusForExperimentTransactional(Long experimentId,
                                                                    RunStatusTypes projectStatus,
                                                                    ProjectStatus stepStatus) {
        return update(
                """
                        projectStatus = ?1,
                        stepStatus = ?2,
                        updatedAt = ?3
                        where experiment.id = ?4
                        """,
                projectStatus,
                stepStatus,
                new Date(),
                experimentId
        );
    }

}
