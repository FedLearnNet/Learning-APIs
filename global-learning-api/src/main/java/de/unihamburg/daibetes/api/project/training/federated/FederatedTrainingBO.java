package de.unihamburg.daibetes.api.project.training.federated;

import bio.cosy.feddb.core.api.project.ProjectStatus;
import bio.cosy.feddb.core.api.socket.LearningClientSyncResponseDTO;
import bio.cosy.feddb.core.execution.event.ExecutionEventPublisher;
import de.unihamburg.daibetes.api.project.membership.ProjectMembershipAO;
import de.unihamburg.daibetes.execution.experiment.ExecutionExperimentAO;
import de.unihamburg.daibetes.execution.experiment.ExecutionExperimentEntity;
import de.unihamburg.daibetes.execution.federation.ExecutionFederationAO;
import de.unihamburg.daibetes.execution.federation.ExecutionParticipantEntity;
import de.unihamburg.daibetes.execution.run.ExecutionRunAO;
import de.unihamburg.daibetes.execution.run.ExecutionRunDetailDTO;
import de.unihamburg.daibetes.execution.run.ExecutionRunDetailMapper;
import de.unihamburg.daibetes.execution.run.ExecutionRunEntity;
import de.unihamburg.daibetes.execution.step.ExecutionRunStepEntity;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Training of a project's workflow across clinics: an experiment of the project with a FEDERATED TRAINING run whose
 * steps are the nodes of a copy of the workflow. Clinics accept it and take part; the run starts once enough did.
 * The clinics run the steps, this moves all of them from step to step through the setup, progress and cleanup BOs.
 */
@ApplicationScoped
public class FederatedTrainingBO {

    @Inject
    ProjectMembershipAO membershipAO;

    @Inject
    ExecutionExperimentAO experimentAO;

    @Inject
    ExecutionRunAO runAO;

    @Inject
    ExecutionRunDetailMapper mapper;

    @Inject
    ExecutionFederationAO federationAO;

    @Inject
    ExecutionEventPublisher events;

    @Inject
    FederatedTrainingSetupBO setupBO;

    @Inject
    FederatedTrainingProgressBO progressBO;

    @Inject
    FederatedTrainingCleanupBO cleanupBO;

    @Transactional
    public List<ExecutionRunDetailDTO> listTransactional(Long projectId, String keycloakId) {
        membershipAO.checkProjectAndUser(projectId, keycloakId);
        return runAO.listFederatedTrainingsOfProject(projectId).stream().map(run -> mapper.mapRunEntityToDetailDTO(run, false)).toList();
    }

    @Transactional
    public ExecutionRunDetailDTO findRunTransactional(Long projectId, Long runId, String keycloakId) {
        membershipAO.checkProjectAndUser(projectId, keycloakId);
        return mapper.mapRunEntityToDetailDTO(runOf(projectId, runId), false);
    }

    @Transactional
    public ExecutionRunDetailDTO createTransactional(Long projectId, CreateProjectFederatedExperimentDTO create, String keycloakId,
                                                     Set<String> roles) {
        membershipAO.checkProjectAndUser(projectId, keycloakId);
        return mapper.mapRunEntityToDetailDTO(setupBO.create(projectId, create, keycloakId, roles), false);
    }

    @Transactional
    public ExecutionRunDetailDTO startTransactional(Long projectId, Long runId, String keycloakId) {
        membershipAO.checkProjectAndUser(projectId, keycloakId);
        ExecutionRunEntity run = runOf(projectId, runId);
        setupBO.start(experimentAO.lock(run.getExperiment()), run);
        announceChanged(run);
        return mapper.mapRunEntityToDetailDTO(run, false);
    }

    @Transactional
    public ExecutionRunDetailDTO stopTransactional(Long projectId, Long runId, String keycloakId) {
        membershipAO.checkProjectAndUser(projectId, keycloakId);
        ExecutionRunEntity run = runOf(projectId, runId);
        cleanupBO.stopTraining(experimentAO.lock(run.getExperiment()), run, ProjectStatus.STOPPED);
        return mapper.mapRunEntityToDetailDTO(run, false);
    }

    @Transactional
    public void acceptTransactional(String globalUniqueId, Integer count, String clinicId, Boolean modelCanBePublic) {
        Optional<ExecutionExperimentEntity> found = experimentAO.findByGlobalUniqueIdLockedOptional(globalUniqueId);
        if (found.isEmpty()) {
            Log.warnf("No training %s to accept", globalUniqueId);
            return;
        }
        ExecutionRunEntity run = runOf(found.get());
        if (setupBO.accept(found.get(), run, count, clinicId, modelCanBePublic)) {
            announceChanged(run);
        }
    }

    @Transactional
    public void updateTransactional(LearningClientSyncResponseDTO update) {
        String globalUniqueId = update.getGlobalUniqueExperimentId();
        String clinicId = update.getUniqueRandomClinicId();
        if (globalUniqueId == null || clinicId == null) {
            Log.warn("Ignoring a clinic update without training or clinic");
            return;
        }
        Optional<ExecutionExperimentEntity> found = experimentAO.findByGlobalUniqueIdLockedOptional(globalUniqueId);
        if (found.isEmpty()) {
            Log.warnf("No training %s to update", globalUniqueId);
            return;
        }
        ExecutionExperimentEntity experiment = found.get();
        ExecutionRunEntity run = runOf(experiment);
        Optional<ExecutionParticipantEntity> participant = run.participant(clinicId);
        if (participant.isEmpty()) {
            Log.warnf("Clinic %s does not take part in training %s", clinicId, globalUniqueId);
            return;
        }
        if (progressBO.applyReport(participant.get(), update) && experiment.getStatus() != ProjectStatus.FINISHED) {
            advance(experiment, run);
        }
    }

    @Transactional
    public Optional<String> findRelayChannelForClientTransactional(String globalUniqueId, String nodeId, String clinicId, String clientId) {
        if (globalUniqueId == null || nodeId == null || clinicId == null || clientId == null) {
            return Optional.empty();
        }
        return federationAO.findRelayChannelOf(globalUniqueId, nodeId, clinicId, clientId);
    }

    // Moves all clinics on by how far they got together.
    private void advance(ExecutionExperimentEntity experiment, ExecutionRunEntity run) {
        Optional<ExecutionRunStepEntity> current = run.getCurrentStep();
        switch (run.participantStatus()) {
            case READY -> progressBO.stepToStart(run).ifPresentOrElse(step -> {
                if (!setupBO.startStep(experiment, run, step)) {
                    cleanupBO.stopTraining(experiment, run, ProjectStatus.ERROR);
                }
            }, () -> Log.infof("Training %d has no step to start", run.getId()));
            case RUNNING -> current.ifPresentOrElse(progressBO::markRunning,
                    () -> cleanupBO.stopTraining(experiment, run, ProjectStatus.ERROR));
            case FINISHED -> current.ifPresentOrElse(step -> nextStep(experiment, run, step),
                    () -> cleanupBO.stopTraining(experiment, run, ProjectStatus.ERROR));
            case ERROR -> cleanupBO.stopTraining(experiment, run, ProjectStatus.ERROR);
            default -> {
            }
        }
        announceChanged(run);
    }

    // All clinics finished the step: the next one is set up, or the training finished.
    private void nextStep(ExecutionExperimentEntity experiment, ExecutionRunEntity run, ExecutionRunStepEntity current) {
        cleanupBO.stopRelay(current.getFederation());
        progressBO.finishStep(run, current).ifPresentOrElse(next -> setupBO.prepareStep(experiment, run, next),
                () -> cleanupBO.finishTraining(experiment, run));
    }

    // The step coordinator leaves federated runs alone, so a changed step only announces the run.
    private void announceChanged(ExecutionRunEntity run) {
        run.getSteps().stream().findFirst().ifPresent(step -> events.stepChanged(step.getId()));
    }

    private ExecutionRunEntity runOf(ExecutionExperimentEntity experiment) {
        return experiment.getRuns().getFirst();
    }

    private ExecutionRunEntity runOf(Long projectId, Long runId) {
        return runAO.findFederatedTrainingOfProjectOptional(runId, projectId)
                .orElseThrow(() -> new NotFoundException("Training " + runId + " not found"));
    }
}
