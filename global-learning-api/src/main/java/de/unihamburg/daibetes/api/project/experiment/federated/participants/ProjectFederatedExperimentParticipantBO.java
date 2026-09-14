package de.unihamburg.daibetes.api.project.experiment.federated.participants;

import bio.cosy.feddb.core.api.project.ProjectStatus;
import bio.cosy.feddb.core.api.run.RunStatusTypes;
import bio.cosy.feddb.core.api.socket.LearningClientSyncResponseDTO;
import bio.cosy.feddb.core.base.BaseBo;
import de.unihamburg.daibetes.api.project.experiment.federated.ProjectFederatedExperimentAO;
import de.unihamburg.daibetes.api.project.experiment.federated.ProjectFederatedExperimentEntity;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;


@ApplicationScoped
public class ProjectFederatedExperimentParticipantBO extends BaseBo<ProjectFederatedExperimentParticipantDTO, ProjectFederatedExperimentParticipantEntity, ProjectFederatedExperimentParticipantAO, ProjectFederatedExperimentParticipantMapper> {

    @Inject
    ProjectFederatedExperimentAO projectFederatedExperimentAO;

    /**
     * Finds all participants of a federated experiment.
     *
     * @param experimentId the ID of the experiment
     * @return a list of ProjectFederatedExperimentParticipantDTO
     */
    public List<ProjectFederatedExperimentParticipantDTO> getAllByExperiment(Long experimentId) {
        return mapper.entitiesToDtos(ao.getAllByExperiment(experimentId));
    }

    /**
     * Adds a participant to a federated experiment.
     *
     * @param uniqueRandomClinicId the unique random clinic ID of the participant
     * @param experiment           the experiment
     * @throws IllegalArgumentException if the experiment is not found
     */
    public ProjectFederatedExperimentParticipantEntity addParticipant(ProjectFederatedExperimentEntity experiment, String uniqueRandomClinicId, Boolean modelCanBePublic) {
        ProjectFederatedExperimentParticipantEntity entity = new ProjectFederatedExperimentParticipantEntity();
        entity.setUniqueRandomClinicId(uniqueRandomClinicId);
        entity.setExperiment(experiment);
        entity.setProjectStatus(RunStatusTypes.INITIALIZED);
        entity.setCurrentNodeId(null);
        entity.setModelCanBePublic(modelCanBePublic);
        return entity;
    }

    /**
     * Updates the status of a participant in a federated experiment.
     *
     * @param updateDTO the data transfer object containing the update information
     */
    public ProjectFederatedExperimentParticipantEntity updateStatus(LearningClientSyncResponseDTO updateDTO) {
        if (updateDTO.getGlobalUniqueExperimentId() == null || updateDTO.getUniqueRandomClinicId() == null) {
            Log.warn("Learning request ID or unique random clinic ID is null in the update DTO");
            return null;
        }
        Optional<ProjectFederatedExperimentEntity> experiment = projectFederatedExperimentAO.findByGlobalUniqueID(
                updateDTO.getGlobalUniqueExperimentId(),
                LockModeType.PESSIMISTIC_WRITE
        );
        if (experiment.isEmpty()) {
            Log.warnf("Experiment not found for experiment ID: %s", updateDTO.getGlobalUniqueExperimentId());
            return null;
        }
        Optional<ProjectFederatedExperimentParticipantEntity> participant = ao.findByExperimentIdAndClinicId(
                updateDTO.getGlobalUniqueExperimentId(),
                updateDTO.getUniqueRandomClinicId()
        );
        if (participant.isEmpty()) {
            Log.warnf("Participant not found for experiment ID: %s and clinic ID: %s", updateDTO.getGlobalUniqueExperimentId(), updateDTO.getUniqueRandomClinicId());
            return null;
        }
        ProjectFederatedExperimentParticipantEntity entity = participant.get();
        ProjectStatus currentStatus = entity.getStepStatus();
        if (ProjectStatus.isFinalStatus(currentStatus)) {
            Log.infof("Current status is already in final state for participant in experiment ID: %s and clinic ID: %s. Current step status: %s",
                    updateDTO.getGlobalUniqueExperimentId(), updateDTO.getUniqueRandomClinicId(), currentStatus);
            return null;
        }
        ProjectStatus newStatus = updateDTO.getStepStatus();
        if (newStatus == null && updateDTO.getError() != null) {
            // Error reports from older clients may carry only the error text. Treat them as ERROR so
            // the experiment is stopped instead of silently dropping the update. Written back onto
            // the DTO because the persist below reads updateDTO.getStepStatus().
            Log.errorf("Participant in experiment ID: %s clinic ID: %s reported an error without step status, treating as ERROR: %s",
                    updateDTO.getGlobalUniqueExperimentId(), updateDTO.getUniqueRandomClinicId(), updateDTO.getError());
            newStatus = ProjectStatus.ERROR;
            updateDTO.setStepStatus(ProjectStatus.ERROR);
        }
        if (newStatus == null) {
            Log.warnf("New status is null for participant in experiment ID: %s and clinic ID: %s", updateDTO.getGlobalUniqueExperimentId(), updateDTO.getUniqueRandomClinicId());
            return null;
        }
        if (updateDTO.getError() != null) {
            Log.errorf("Participant in experiment ID: %s clinic ID: %s reported error: %s",
                    updateDTO.getGlobalUniqueExperimentId(), updateDTO.getUniqueRandomClinicId(), updateDTO.getError());
        }
        String currentNodeId = entity.getCurrentNodeId();
        String updatedNodeId = updateDTO.getCurrentNodeId();
        if (currentNodeId != null && updatedNodeId == null && ProjectStatus.isPreRunning(newStatus)) {
            Log.warnf("Ignoring stale pre-running update without node id for participant in experiment ID: %s and clinic ID: %s. Expected node: %s",
                    updateDTO.getGlobalUniqueExperimentId(), updateDTO.getUniqueRandomClinicId(), currentNodeId);
            return null;
        }
        if (currentNodeId != null && updatedNodeId != null && !currentNodeId.equals(updatedNodeId)) {
            Log.warnf("Ignoring out-of-sync update for participant in experiment ID: %s and clinic ID: %s. Expected node: %s, received node: %s",
                    updateDTO.getGlobalUniqueExperimentId(), updateDTO.getUniqueRandomClinicId(), currentNodeId, updatedNodeId);
            return null;
        }
        Log.infof("Updating status for participant in experiment ID: %s and clinic ID: %s from %s to %s",
                updateDTO.getGlobalUniqueExperimentId(), updateDTO.getUniqueRandomClinicId(), currentStatus, newStatus);
        if (entity.getStepStatus() != null && entity.getStepStatus().equals(updateDTO.getStepStatus())) {
            Log.infof("No status change for participant in experiment ID: %s and clinic ID: %s. Current step status: %s",
                    updateDTO.getGlobalUniqueExperimentId(), updateDTO.getUniqueRandomClinicId(), entity.getStepStatus());
            if (currentStatus.equals(ProjectStatus.RUNNING)) {
                return entity;
            }
            return null;
        }
        if (entity.getStepStatus() != null && currentStatus.equals(ProjectStatus.RUNNING) && newStatus.equals(ProjectStatus.READY)) {
            Log.warnf("Ignoring invalid status transition from RUNNING to READY for participant in experiment ID: %s and clinic ID: %s",
                    updateDTO.getGlobalUniqueExperimentId(), updateDTO.getUniqueRandomClinicId());
            return entity;
        }
        Long participantId = entity.getId();
        ao.getEntityManager().detach(entity);
        boolean updated = ao.updateStatusTransactional(
                participantId,
                updateDTO.getProjectStatus(),
                updateDTO.getCurrentNodeId(),
                updateDTO.getStepStatus()
        );
        if (!updated) {
            Log.warnf("Participant status update affected no rows for experiment ID: %s and clinic ID: %s",
                    updateDTO.getGlobalUniqueExperimentId(), updateDTO.getUniqueRandomClinicId());
            return null;
        }
        return ao.findByIdOptional(participantId).orElse(null);
    }

}
