package de.unihamburg.daibetes.api.project.experiment.federated;

import bio.cosy.feddb.core.api.project.ProjectDetailDTO;
import bio.cosy.feddb.core.api.project.ProjectStatus;
import bio.cosy.feddb.core.api.run.RunStatusTypes;
import bio.cosy.feddb.core.api.socket.FederatedLearningRelayInfoDTO;
import bio.cosy.feddb.core.api.socket.ProjectFederatedExperimentForLocalDTO;
import bio.cosy.feddb.core.api.workflow.WorkflowDTO;
import bio.cosy.feddb.core.api.workflow.base.BaseWorkflowEngine;
import bio.cosy.feddb.core.api.workflow.base.WorkflowException;
import bio.cosy.feddb.core.api.workflow.node.WorkflowNodeDetailDTO;
import bio.cosy.feddb.core.base.BaseBo;
import de.unihamburg.daibetes.api.feddbclient.FLNetClientBroadcastBO;
import de.unihamburg.daibetes.api.project.ProjectBO;
import de.unihamburg.daibetes.api.project.experiment.federated.participants.ProjectFederatedExperimentParticipantAO;
import de.unihamburg.daibetes.api.project.experiment.federated.participants.ProjectFederatedExperimentParticipantBO;
import de.unihamburg.daibetes.api.project.experiment.federated.participants.ProjectFederatedExperimentParticipantEntity;
import de.unihamburg.daibetes.api.project.experiment.federated.step.ProjectFederatedExperimentStepBO;
import de.unihamburg.daibetes.api.project.experiment.federated.step.ProjectFederatedExperimentStepDTO;
import de.unihamburg.daibetes.api.project.experiment.federated.step.ProjectFederatedExperimentStepEntity;
import de.unihamburg.daibetes.api.project.membership.ProjectMembershipAO;
import de.unihamburg.daibetes.api.workflow.WorkflowBO;
import de.unihamburg.daibetes.api.workflow.WorkflowEntity;
import de.unihamburg.daibetes.config.FLNetConfig;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.LockModeType;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.NotAllowedException;
import jakarta.ws.rs.NotFoundException;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.OnOverflow;

import java.util.*;

import static de.unihamburg.daibetes.api.project.experiment.ProjectExperimentServiceImpl.EXPERIMENT_FED_CHANNEL;


@ApplicationScoped
public class ProjectFederatedExperimentBO extends BaseBo<ProjectFederatedExperimentDTO, ProjectFederatedExperimentEntity, ProjectFederatedExperimentAO, ProjectFederatedExperimentMapper> {

    @Inject
    FLNetConfig config;

    @Inject
    FLNetClientBroadcastBO fedDBClientBroadcastBO;

    @Inject
    ProjectFederatedExperimentParticipantBO projectFederatedExperimentParticipantBO;

    @Inject
    ProjectFederatedExperimentParticipantAO projectFederatedExperimentParticipantAO;

    @Inject
    ProjectMembershipAO projectMembershipAO;

    @Inject
    ProjectBO projectBO;

    @Inject
    WorkflowBO workflowBO;

    @ConfigProperty(name = "quarkus.rest-client.relay-api.url", defaultValue = "Default Relay Server")
    String relayServerAddress;

    @Inject
    ProjectFederatedExperimentStepBO projectFederatedExperimentStepBO;

    @Inject
    @Channel(EXPERIMENT_FED_CHANNEL)
    @OnOverflow(OnOverflow.Strategy.DROP)
    Emitter<ProjectFederatedExperimentDetailDTO> infoEmitter;

    protected static final BaseWorkflowEngine baseWorkflowEngine = new BaseWorkflowEngine();

    /*TODO  @Inject
       @RestClient
       ControllerService controllerService;
   */
    public List<ProjectFederatedExperimentDTO> getAllByProject(Long id, String keycloakId) {
        projectMembershipAO.checkProjectAndUser(id, keycloakId);

        return mapper.entitiesToDtos(ao.findByProjectId(id));
    }

    public ProjectFederatedExperimentDetailDTO getById(Long projectId, Long id, String keycloakId) {
        projectMembershipAO.checkProjectAndUser(projectId, keycloakId);

        Optional<ProjectFederatedExperimentEntity> entity = ao.findByIdOptional(id);
        if (entity.isEmpty()) {
            throw new NotFoundException(String.format("Experiment ID %s not found", id));
        }
        return entityToDetailDto(entity.get());
    }

    public ProjectFederatedExperimentDetailDTO entityToDetailDto(ProjectFederatedExperimentEntity entity) {
        ProjectFederatedExperimentDetailDTO experiment = mapper.entityToDetailDto(entity);
        experiment.setProjectVersion(projectBO.getById(experiment.getProjectId()));
        return experiment;
    }

    public ProjectFederatedExperimentStepDTO getStepById(Long projectId, Long experimentId, Long stepId, String keycloakId) {
        projectMembershipAO.checkProjectAndUser(projectId, keycloakId);

        ProjectFederatedExperimentStepDTO dto = projectFederatedExperimentStepBO.getById(stepId);
        if (dto.getExperimentId().equals(experimentId)) {
            return dto;
        } else {
            throw new NotFoundException(String.format("Step ID %s not found for Experiment ID %s", stepId, experimentId));
        }
    }


    public ProjectFederatedExperimentDTO create(Long projectId,
                                                CreateProjectFederatedExperimentDTO dto,
                                                String keycloakId,
                                                Set<String> roles) {
        projectMembershipAO.checkProjectAndUser(projectId, keycloakId);


        ProjectDetailDTO projectDetail = projectBO.getById(projectId);
        WorkflowDTO workflow = workflowBO.getById(projectDetail.getWorkflowId());
        WorkflowEntity clonedWorkflow = workflowBO.cloneWorkflow(workflow);
        String channelId = ProjectFederatedExperimentHelper.createRandomChannelID();

        ProjectFederatedExperimentDTO createDto = mapper.createDtoToDto(dto, projectId, channelId, relayServerAddress);
        Log.info("Creating federated experiment for project " + projectId + " with channel ID " + channelId);
        projectDetail.setWorkflowId(clonedWorkflow.getId());
        createDto.setProjectVersion(projectDetail);
        ProjectFederatedExperimentEntity entity = mapper.dtoToEntity(createDto);
        entity.setWorkflow(clonedWorkflow);
        entity.setGlobalUniqueId(UUID.randomUUID().toString());
        ao.persist(entity);
        createDto = mapper.entityToDto(entity);
        Log.info("Created ProjectFederatedExperimentDTO: " + createDto);
        workflow = workflowBO.entityToDto(clonedWorkflow);

        ProjectFederatedExperimentForLocalDTO simpleDto = mapper.dtoToSimpleDTO(createDto,
                keycloakId,
                roles,
                workflow
        );
        fedDBClientBroadcastBO.fireLearningQuery(simpleDto);
        return createDto;
    }


    /**
     * Starts the learning process of a federated experiment.
     * The status is set to PREPARE.
     *
     * @param projectId    the project ID
     * @param experimentId the experiment ID
     * @param keycloakId   the keycloak ID
     * @return the updated ProjectFederatedExperimentDTO
     */
    public ProjectFederatedExperimentDetailDTO startLearning(Long projectId,
                                                             Long experimentId,
                                                             String keycloakId) {

        projectMembershipAO.checkProjectAndUser(projectId, keycloakId);

        Optional<ProjectFederatedExperimentEntity> entityOptional = ao.findByIdOptional(experimentId, LockModeType.PESSIMISTIC_WRITE);
        if (entityOptional.isEmpty() || !entityOptional.get().getExperimentStatus().equals(ProjectStatus.READY)) {
            throw new NotAllowedException("Experiment is not ready");
        }
        ProjectFederatedExperimentEntity entity = entityOptional.get();

        List<ProjectFederatedExperimentParticipantEntity> participants = projectFederatedExperimentParticipantAO.getAllByExperiment(experimentId);
        if (participants.size() < config.federatedLearning().participantsMinAmount()) {
            throw new NotAllowedException("Not enough participants");
        }
        if (entity.getWorkflow() == null || entity.getWorkflow().getNodes().isEmpty()) {
            throw new NotAllowedException("Experiment workflow is missing");
        }
        int randomIndex = new Random().nextInt(participants.size());
        ProjectFederatedExperimentParticipantEntity coordinator = participants.get(randomIndex);
        coordinator.setIsCoordinator(true);
        projectFederatedExperimentStepBO.createForWorkflow(entity);
        ao.getEntityManager().detach(entity);
        ao.startLearningTransactional(experimentId, coordinator);
        ProjectFederatedExperimentEntity updatedEntity = ao.findById(experimentId);
        // IF coordinator logic is setted in the project
        fedDBClientBroadcastBO.startLearning(updatedEntity.getGlobalUniqueId(), coordinator.getUniqueRandomClinicId(), updatedEntity.getModelCanBePublic());

        //TODO implement case if plattform ist controller
        //TODO FOR THIS CASE WE NEED TO CHANGE ALS BO TO BaseWorkflowExperimentBO

        return entityToDetailDto(updatedEntity);
    }

    /**
     * stops the learning process of a federated experiment.
     * The status is set to PREPARE.
     *
     * @param projectId    the project ID
     * @param experimentId the experiment ID
     * @param keycloakId   the keycloak ID
     * @return the updated ProjectFederatedExperimentDTO
     */
    public ProjectFederatedExperimentDetailDTO stopLearning(Long projectId,
                                                            Long experimentId,
                                                            String keycloakId) {

        projectMembershipAO.checkProjectAndUser(projectId, keycloakId);

        Optional<ProjectFederatedExperimentEntity> entityOptional = ao.findByIdOptional(experimentId, LockModeType.PESSIMISTIC_WRITE);
        if (entityOptional.isEmpty()) {
            throw new NotAllowedException("Experiment is not found");
        }
        ProjectFederatedExperimentEntity entity = entityOptional.get();
        return stopLearning(entity, ProjectStatus.STOPPED);
    }

    public ProjectFederatedExperimentDetailDTO stopLearning(ProjectFederatedExperimentEntity entity, ProjectStatus reason) {
        Long experimentId = entity.getId();
        String globalUniqueId = entity.getGlobalUniqueId();
        Log.infof("Stopping FED experiment %d (globalId %s) with reason %s", experimentId, globalUniqueId, reason);
        Long currentStepId = entity.getCurrentWorkflowNode() != null ? entity.getCurrentWorkflowNode().getId() : null;

        if (currentStepId != null) {
            projectFederatedExperimentStepBO.persistStep(currentStepId, RunStatusTypes.STOPPED);
        }
        projectFederatedExperimentParticipantAO.updateProjectAndStepStatusForExperimentTransactional(
                experimentId,
                RunStatusTypes.STOPPED,
                reason
        );
        ao.getEntityManager().detach(entity);
        if (reason == ProjectStatus.ERROR) {
            ao.markExperimentError(experimentId);
        } else if (reason == ProjectStatus.STOPPED) {
            ao.markExperimentStop(experimentId);
        } else {
            ao.updateStatusTransactional(experimentId, reason);
        }

        // Broadcast next step to all participants
        Log.infof("All participants stopping FED experiment %d", experimentId);
        fedDBClientBroadcastBO.stopLearning(globalUniqueId);
        ProjectFederatedExperimentEntity updatedEntity = ao.findById(experimentId);
        ProjectFederatedExperimentDetailDTO detail = entityToDetailDto(updatedEntity);
        sendMessage(detail);
        return detail;
    }

    /**
     * Updates the acceptance count of a federated experiment.
     * If more than clinics have accepted the experiment, the status is set to READY.
     *
     * @param globalUniqueExperimentId the experiment ID
     * @param count                    the count to add
     * @param uniqueRandomClinicId     the unique random clinic ID of the participant
     */
    public void updateCount(String globalUniqueExperimentId, Integer count, String uniqueRandomClinicId, Boolean modelCanBePublic) {
        if (count == null || count <= 0) {
            if (config.federatedLearning().participantsMinDataCount() < 0) {
                Log.warnf("Count is null or non-positive for experiment ID: %s", globalUniqueExperimentId);
                return;
            } else {
                Log.infof("Count is null or non-positive for experiment ID: %s, but accepting it", globalUniqueExperimentId);
            }
        }
        if (config.federatedLearning().participantsMinDataCount() >= 0 && count < config.federatedLearning().participantsMinDataCount()) {
            Log.warnf("Count %d is below the minimum data count for experiment ID: %s", count, globalUniqueExperimentId);
            return;
        }
        if (uniqueRandomClinicId == null || uniqueRandomClinicId.isEmpty()) {
            Log.warnf("Unique random clinic ID is null or empty for experiment ID: %s", globalUniqueExperimentId);
            return;
        }
        Log.infof("Updating experiment count for ID: %s with count: %d from clinic: %s", globalUniqueExperimentId, count, uniqueRandomClinicId);
        Optional<ProjectFederatedExperimentEntity> entityOptional = ao.findByGlobalUniqueID(globalUniqueExperimentId, LockModeType.PESSIMISTIC_WRITE);
        if (entityOptional.isEmpty()) {
            Log.warnf("No experiment found with ID: %s", globalUniqueExperimentId);
            return;
        }
        ProjectFederatedExperimentEntity entity = entityOptional.get();
        Long currentCount = Optional.ofNullable(entity.getAcceptanceCount()).orElse(0L);
        Long currentClinicCount = Optional.ofNullable(entity.getAcceptanceClinicCount()).orElse(0L);
        int safeCount = count == null ? 0 : Math.max(count, 0);
        Long updatedCount = currentCount + safeCount;
        if (Boolean.TRUE.equals(entity.getModelNeedToBePublic()) && Boolean.FALSE.equals(modelCanBePublic)) {
            Log.warnf("Ignoring clinic %s for experiment %s: model needs to be public", uniqueRandomClinicId, globalUniqueExperimentId);
            return;
        }

        // A clinic can report patients in several steps when its cohorts decide independently.
        // Reuse the existing participant and only grow the acceptance count.
        Optional<ProjectFederatedExperimentParticipantEntity> existingParticipant =
                projectFederatedExperimentParticipantAO.findByExperimentIdAndClinicId(
                        globalUniqueExperimentId, uniqueRandomClinicId);
        boolean newClinic = existingParticipant.isEmpty();
        Long updatedClinicCount = newClinic ? currentClinicCount + 1 : currentClinicCount;

        Boolean updatedModelCanBePublic = entity.getModelCanBePublic();
        if (updatedModelCanBePublic == null) {
            updatedModelCanBePublic = true;
        }
        if (Boolean.FALSE.equals(modelCanBePublic)) {
            updatedModelCanBePublic = false;
        }

        ProjectStatus updatedExperimentStatus = entity.getExperimentStatus();
        if (updatedClinicCount >= config.federatedLearning().participantsMinAmount()) {
            Log.infof("Experiment %s reached required clinic count. Setting status to READY", globalUniqueExperimentId);
            updatedExperimentStatus = ProjectStatus.READY;
        }

        if (newClinic) {
            ProjectFederatedExperimentParticipantEntity participant =
                    projectFederatedExperimentParticipantBO.addParticipant(
                            entity, uniqueRandomClinicId, modelCanBePublic);
            projectFederatedExperimentParticipantAO.persist(participant);
            projectFederatedExperimentParticipantAO.getEntityManager().flush();
        } else if (Boolean.FALSE.equals(modelCanBePublic)
                && !Boolean.FALSE.equals(existingParticipant.get().getModelCanBePublic())) {
            existingParticipant.get().setModelCanBePublic(false);
        }

        ao.getEntityManager().detach(entity);
        ao.updateAcceptanceTransactional(
                entity.getId(),
                updatedCount,
                updatedClinicCount,
                updatedModelCanBePublic,
                updatedExperimentStatus
        );
        ProjectFederatedExperimentEntity updatedEntity = ao.findById(entity.getId());

        Log.infof("Successfully updated experiment %s. New acceptance count: %d, clinic count: %d (newClinic=%s)",
                globalUniqueExperimentId, updatedEntity.getAcceptanceCount(),
                updatedEntity.getAcceptanceClinicCount(), newClinic);

        sendMessage(updatedEntity);
    }

    public void updateStepAndNotify(ProjectFederatedExperimentParticipantEntity entity) {
        // Validate input entity is not null
        if (entity == null) {
            Log.errorf("Cannot check for start experiment step, entity is null");
            return;
        }

        // Get and validate associated experiment
        Long experimentId = entity.getExperiment() != null ? entity.getExperiment().getId() : null;
        if (experimentId == null) {
            Log.errorf("Cannot check for start federated experiment step, experiment is null");
            return;
        }
        Optional<ProjectFederatedExperimentEntity> experimentOptional = ao.findByIdOptional(experimentId, LockModeType.PESSIMISTIC_WRITE);
        if (experimentOptional.isEmpty()) {
            Log.errorf("Cannot check for start federated experiment step, experiment %d not found", experimentId);
            return;
        }
        ProjectFederatedExperimentEntity experiment = experimentOptional.get();
        // Check if experiment is already completed
        if (experiment.getExperimentStatus().equals(ProjectStatus.FINISHED)) {
            Log.infof("Experiment {} is already in FINISHED state - cannot start", experiment.getId());
            return;
        }

        ProjectStatus newStatus = entity.getStepStatus();
        Set<ProjectFederatedExperimentParticipantEntity> participants = new HashSet<>(
                projectFederatedExperimentParticipantAO.getAllByExperiment(experimentId)
        );
        if (participants == null) {
            if (newStatus.equals(ProjectStatus.ERROR)) {
                stopLearning(experiment, ProjectStatus.ERROR);
                return;
            }
        } else {
            newStatus = ProjectFederatedExperimentHelper.getNextStatus(participants);
        }
        syncCurrentStepStatus(experiment, newStatus);

        //check if all are Ready
        if (newStatus.equals(ProjectStatus.READY)) {
            handleStartRunning(participants, experiment);
            sendMessage(reloadExperiment(experimentId));
            return;
        }
        //check if all is running
        if (newStatus.equals(ProjectStatus.RUNNING)) {
            if (experiment.getCurrentWorkflowNode() == null) {
                Log.errorf("Cannot start relay setup for experiment %d, current workflow node is null", experiment.getId());
                stopLearning(experiment, ProjectStatus.ERROR);
                return;
            }

            sendMessage(reloadExperiment(experimentId));
            return;
        }
        //check if one has error
        if (newStatus.equals(ProjectStatus.ERROR)) {
            Log.errorf("Participant %s reported status %s for experiment %d - stopping learning with ERROR",
                    entity.getUniqueRandomClinicId(), entity.getStepStatus(), experiment.getId());
            stopLearning(experiment, ProjectStatus.ERROR);
            return;
        }
        //check if all are finished
        if (newStatus.equals(ProjectStatus.FINISHED)) {
            if (experiment.getCurrentWorkflowNode() == null) {
                Log.errorf("Cannot proceed to next step for experiment %d, current workflow node is null", experiment.getId());
                stopLearning(experiment, ProjectStatus.ERROR);
                return;
            }
            handleNextStep(participants, experiment);
            return;
        }
        sendMessage(reloadExperiment(experimentId));
    }

    /**
     * Checks and manages the initiation of a running state for federated experiment participants.
     * This method verifies that all participants are ready and synchronized before starting the learning step.
     *
     * @param participants The participant entity requesting to start running
     * @throws IllegalArgumentException if entity is null or invalid
     * @throws IllegalStateException    if experiment state prevents running
     */
    public void handleStartRunning(@NotNull Set<ProjectFederatedExperimentParticipantEntity> participants, ProjectFederatedExperimentEntity experiment) {
        WorkflowDTO workflow = getWorkflowDTO(experiment);
        WorkflowNodeDetailDTO nextNode = getNextStep(workflow, experiment,
                experiment.getCurrentWorkflowNode());
        if (nextNode == null) {
            Log.infof("No start node found in workflow for FED experiment ID %d", experiment.getId());
            return;
        }
        Map<String, FederatedLearningRelayInfoDTO> relayData;
        try {
            relayData = projectFederatedExperimentStepBO.handleRelaySetup(experiment);
        } catch (Exception e) {
            Log.errorf(e, "Relay setup failed for experiment %d (current node %s) - stopping learning with ERROR: %s",
                    experiment.getId(),
                    experiment.getCurrentWorkflowNode() != null ? experiment.getCurrentWorkflowNode().getId() : null,
                    e.getMessage());
            stopLearning(experiment, ProjectStatus.ERROR);
            return;
        }
        if (relayData == null || relayData.isEmpty()) {
            Log.infof("No relay information required for experiment %d (node %s)",
                    experiment.getId(), nextNode.getNodeId());
        }
        projectFederatedExperimentParticipantAO.updateStepStatusForExperimentTransactional(
                experiment.getId(),
                nextNode.getNodeId(),
                ProjectStatus.RUNNING
        );
        syncWorkflowStepStatus(experiment.getId(), nextNode.getNodeId(), RunStatusTypes.STARTED);

        // Broadcast start learning to all participants
        Log.infof("All participants ready - starting node {} for experiment {}", nextNode.getId(), experiment.getId());
        fedDBClientBroadcastBO.startStepLearning(experiment.getGlobalUniqueId(), nextNode.getId(), nextNode.getNodeId(), relayData);

    }


    /**
     * Checks and manages the transition to the next step in a federated experiment workflow.
     * This method evaluates if all participants have completed the current step and coordinates
     * the transition to the next step or experiment completion.
     *
     * @param participants The participants entity requesting to move to the next step
     * @throws IllegalStateException if preconditions for next step are not met
     */
    public void handleNextStep(@NotNull Set<ProjectFederatedExperimentParticipantEntity> participants, ProjectFederatedExperimentEntity experiment) {
        WorkflowDTO workflow = getWorkflowDTO(experiment);
        WorkflowNodeDetailDTO nextNode = getNextStep(workflow, experiment, experiment.getCurrentWorkflowNode());
        Long experimentId = experiment.getId();
        Long currentStepId = experiment.getCurrentWorkflowNode().getId();
        projectFederatedExperimentStepBO.persistStep(currentStepId, RunStatusTypes.FINISHED);
        if (nextNode == null) {
            //FINISHED LEARNING
            projectFederatedExperimentParticipantAO.updateProjectAndStepStatusForExperimentTransactional(
                    experimentId,
                    RunStatusTypes.FINISHED,
                    ProjectStatus.FINISHED
            );
            ao.getEntityManager().detach(experiment);
            ao.markExperimentFinish(experimentId);
            ProjectFederatedExperimentEntity updatedExperiment = ao.findById(experimentId);
            sendMessage(updatedExperiment);
            return;
        }

        projectFederatedExperimentParticipantAO.updateStepStatusForExperimentTransactional(
                experimentId,
                nextNode.getNodeId(),
                ProjectStatus.INIT
        );
        ao.getEntityManager().detach(experiment);
        ProjectFederatedExperimentEntity updatedExperiment = ao.findById(experimentId);
        sendMessage(updatedExperiment);

        // Broadcast next step to all participants
        Log.infof("All participants ready - initiating step {} for experiment {}", nextNode.getId(), experiment.getId());
        fedDBClientBroadcastBO.nextStepLearning(experiment.getGlobalUniqueId(), nextNode.getId(), nextNode.getNodeId());
    }

    private void syncCurrentStepStatus(ProjectFederatedExperimentEntity experiment, ProjectStatus participantStatus) {
        if (experiment == null || experiment.getCurrentWorkflowNode() == null || participantStatus == null) {
            return;
        }
        RunStatusTypes stepStatus = mapParticipantStatusToRunStatus(participantStatus);
        if (stepStatus == null) {
            return;
        }
        projectFederatedExperimentStepBO.persistStep(experiment.getCurrentWorkflowNode().getId(), stepStatus);
    }

    private void syncWorkflowStepStatus(Long experimentId, String workflowNodeId, RunStatusTypes status) {
        projectFederatedExperimentStepBO.findByWorkflowNodeId(experimentId, workflowNodeId)
                .ifPresent(step -> projectFederatedExperimentStepBO.persistStep(step.getId(), status));
    }

    private RunStatusTypes mapParticipantStatusToRunStatus(ProjectStatus participantStatus) {
        return switch (participantStatus) {
            case READY, INIT, PREPARE -> RunStatusTypes.STARTED;
            case RUNNING -> RunStatusTypes.RUNNING;
            case FINISHED -> RunStatusTypes.FINISHED;
            case ERROR -> RunStatusTypes.ERROR;
            case STOPPED, SHUTDOWN -> RunStatusTypes.STOPPED;
        };
    }

    private ProjectFederatedExperimentEntity reloadExperiment(Long experimentId) {
        return ao.findById(experimentId);
    }


    private WorkflowNodeDetailDTO getNextStep(WorkflowDTO workflow,
                                              ProjectFederatedExperimentEntity experiment,
                                              ProjectFederatedExperimentStepEntity prevStep) {
        Long experimentId = experiment.getId();
        WorkflowNodeDetailDTO node = null;
        if (prevStep == null) {
            node = baseWorkflowEngine.firstNode(workflow);
            if (node == null) {
                Log.errorf("No starting node found in workflow for FED experiment ID %d", experimentId);
                throw new WorkflowException("No starting node found in workflow for FED experiment ID: " + experimentId);
            }
            setCurrentStep(experiment, node);
        } else {
            String prevNodeId = prevStep.getWorkflowNode().getNodeId();
            if (prevStep.getStepStatus().equals(RunStatusTypes.INITIALIZED)) {
                return baseWorkflowEngine.findByNodeId(workflow, prevNodeId);
            }
            node = baseWorkflowEngine.nextNode(workflow, prevNodeId);
            if (node == null) {
                Log.infof("No next node found in workflow for FED experiment ID %d after node %s", experimentId, prevNodeId);
                return null;
            }
            setCurrentStep(experiment, node);
        }
        return node;
    }

    private void setCurrentStep(ProjectFederatedExperimentEntity experiment, WorkflowNodeDetailDTO node) {
        Long experimentId = experiment.getId();
        Optional<ProjectFederatedExperimentStepEntity> stepOptional = projectFederatedExperimentStepBO.findByWorkflowNodeId(experimentId,
                node.getNodeId());

        if (stepOptional.isEmpty()) {
            Log.errorf("WorkflowNode not found for experimentId %d and nodeId %s", experimentId, node.getNodeId());
            throw new WorkflowException("WorkflowNode not found for experimentId " + experimentId + " and nodeId " + node.getNodeId());
        }
        if (experiment.getCurrentWorkflowNode() == null) {
            ao.markExperimentRunning(experimentId);
        }
        ao.setCurrentWorkflowNode(experimentId, stepOptional.get());
        experiment.setCurrentWorkflowNode(stepOptional.get());
    }

    protected WorkflowDTO getWorkflowDTO(ProjectFederatedExperimentEntity experiment) {
        return workflowBO.entityToDto(experiment.getWorkflow());
    }

    public void sendMessage(ProjectFederatedExperimentEntity entity) {
        sendMessage(entityToDetailDto(entity));
    }

    public void sendMessage(ProjectFederatedExperimentDetailDTO info) {
        try {
            infoEmitter.send(info);
            Log.debug("SSE event sent: " + info);
        } catch (Exception e) {
            Log.debug("Attempted to send SSE event, but client connection was already closed.", e);
        }
    }
}
