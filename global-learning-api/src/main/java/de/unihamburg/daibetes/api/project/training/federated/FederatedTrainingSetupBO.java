package de.unihamburg.daibetes.api.project.training.federated;

import bio.cosy.feddb.core.api.project.ProjectDetailDTO;
import bio.cosy.feddb.core.api.project.ProjectStatus;
import bio.cosy.feddb.core.api.socket.FederatedLearningRelayInfoDTO;
import bio.cosy.feddb.core.api.socket.ProjectFederatedExperimentForLocalDTO;
import bio.cosy.feddb.core.execution.ExecutionLocation;
import bio.cosy.feddb.core.execution.ExecutionMode;
import bio.cosy.feddb.core.execution.ExecutionPurpose;
import bio.cosy.feddb.core.execution.executor.ExecutionStartDTO;
import bio.cosy.feddb.core.execution.run.types.RunStatusTypes;
import bio.cosy.feddb.core.services.controller.CreateFLLearningRelayServerRequestDTO;
import bio.cosy.feddb.core.services.controller.CreateFLLearningRelayServerResponseDTO;
import bio.cosy.feddb.core.services.controller.RelayServerAppVersions;
import de.unihamburg.daibetes.api.app.version.FederatedAppVersionEntity;
import de.unihamburg.daibetes.api.feddbclient.FLNetClientBroadcastBO;
import de.unihamburg.daibetes.api.project.ProjectAO;
import de.unihamburg.daibetes.api.project.ProjectBO;
import de.unihamburg.daibetes.api.workflow.WorkflowBO;
import de.unihamburg.daibetes.api.workflow.WorkflowEntity;
import de.unihamburg.daibetes.api.workflow.node.WorkflowNodeEntity;
import de.unihamburg.daibetes.config.FLNetConfig;
import de.unihamburg.daibetes.execution.experiment.ExecutionExperimentAO;
import de.unihamburg.daibetes.execution.experiment.ExecutionExperimentEntity;
import de.unihamburg.daibetes.execution.experiment.ExecutionExperimentMapper;
import de.unihamburg.daibetes.execution.federation.ExecutionFederationEntity;
import de.unihamburg.daibetes.execution.federation.ExecutionFederationMapper;
import de.unihamburg.daibetes.execution.federation.ExecutionParticipantEntity;
import de.unihamburg.daibetes.execution.run.ExecutionRunEntity;
import de.unihamburg.daibetes.execution.run.WorkflowExecutionBO;
import de.unihamburg.daibetes.execution.step.ExecutionRunStepEntity;
import de.unihamburg.daibetes.helper.HashUtil;
import de.unihamburg.daibetes.services.GlobalRelayService;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotAllowedException;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.security.SecureRandom;
import java.util.*;
import java.util.stream.Collectors;

@ApplicationScoped
public class FederatedTrainingSetupBO {

    private static final SecureRandom RANDOM = new SecureRandom();

    @Inject
    FLNetConfig config;

    @Inject
    FLNetClientBroadcastBO broadcastBO;

    @Inject
    ProjectAO projectAO;

    @Inject
    ProjectBO projectBO;

    @Inject
    WorkflowBO workflowBO;

    @Inject
    WorkflowExecutionBO workflowExecutionBO;

    @Inject
    ExecutionExperimentAO experimentAO;

    @Inject
    ExecutionExperimentMapper experimentMapper;

    @Inject
    ExecutionFederationMapper federationMapper;

    @Inject
    @RestClient
    GlobalRelayService globalRelayService;

    @ConfigProperty(name = "quarkus.rest-client.relay-api.url", defaultValue = "Default Relay Server")
    String relayServerAddress;

    /** A training on a copy of the project's workflow; the clinics are asked to accept it. */
    public ExecutionRunEntity create(Long projectId, CreateProjectFederatedExperimentDTO create, String keycloakId, Set<String> roles) {
        ProjectDetailDTO project = projectBO.getById(projectId);
        WorkflowEntity workflow = workflowBO.cloneWorkflow(workflowBO.getById(project.getWorkflowId()));
        project.setWorkflowId(workflow.getId());

        ExecutionExperimentEntity experiment = experimentMapper.federatedCreateDtoToEntity(create, keycloakId, projectAO.findById(projectId),
                project, workflow, relayServerAddress, HashUtil.randomHex(32));
        experimentAO.persist(experiment);

        ExecutionRunEntity run = workflowExecutionBO.createTransactional(
                new ExecutionStartDTO(ExecutionPurpose.TRAINING, ExecutionMode.FEDERATED, create.getName()), workflow.getNodes());
        // The clinics run it, never the coordinators of the platform.
        run.setLocation(ExecutionLocation.LOCAL);
        run.setKeycloakId(keycloakId);
        run.setExperiment(experiment);
        experiment.getRuns().add(run);
        Log.infof("Created federated training %d of project %d", run.getId(), projectId);

        ProjectFederatedExperimentForLocalDTO query = experimentMapper.experimentToLearningQuery(experiment, roles, workflowBO.entityToDto(workflow));
        broadcastBO.sendAfterCommit("Learning query of training " + run.getId(), () -> broadcastBO.fireLearningQuery(query));
        return run;
    }

    /**
     * A clinic accepted the training with its number of patients; false when it does not count. Once enough clinics did,
     * it is ready to start. A clinic may report patients in several parts when its cohorts decide on their own, it then
     * takes part once.
     */
    public boolean accept(ExecutionExperimentEntity experiment, ExecutionRunEntity run, Integer count, String clinicId, Boolean modelCanBePublic) {
        String globalUniqueId = experiment.getGlobalUniqueId();
        int minCount = config.federatedLearning().participantsMinDataCount();
        if ((count == null || count <= 0) && minCount < 0) {
            Log.warnf("Ignoring acceptance of training %s without patients", globalUniqueId);
            return false;
        }
        if (minCount >= 0 && (count == null ? 0 : count) < minCount) {
            Log.warnf("Ignoring acceptance of training %s with %d patients, below the minimum", globalUniqueId, count);
            return false;
        }
        if (clinicId == null || clinicId.isEmpty()) {
            Log.warnf("Ignoring acceptance of training %s without clinic", globalUniqueId);
            return false;
        }
        if (Boolean.TRUE.equals(experiment.getModelNeedToBePublic()) && Boolean.FALSE.equals(modelCanBePublic)) {
            Log.warnf("Ignoring clinic %s for training %s: the model needs to be public", clinicId, globalUniqueId);
            return false;
        }
        Optional<ExecutionParticipantEntity> participant = run.participant(clinicId);
        if (participant.isEmpty()) {
            run.addParticipant(federationMapper.clinicToParticipantEntity(clinicId, modelCanBePublic));
            experiment.setAcceptanceClinicCount(Optional.ofNullable(experiment.getAcceptanceClinicCount()).orElse(0L) + 1);
        } else if (Boolean.FALSE.equals(modelCanBePublic)) {
            participant.get().setModelCanBePublic(false);
        }
        experiment.setAcceptanceCount(Optional.ofNullable(experiment.getAcceptanceCount()).orElse(0L) + Math.max(count == null ? 0 : count, 0));
        if (Boolean.FALSE.equals(modelCanBePublic)) {
            experiment.setModelCanBePublic(false);
        }
        if (experiment.getStatus() == ProjectStatus.INIT
                && experiment.getAcceptanceClinicCount() >= config.federatedLearning().participantsMinAmount()) {
            experiment.setStatus(ProjectStatus.READY);
        }
        Log.infof("Clinic %s accepted training %s, %d patients of %d clinics now", clinicId, globalUniqueId,
                experiment.getAcceptanceCount(), experiment.getAcceptanceClinicCount());
        return true;
    }

    /** Starts a training enough clinics accepted; one of them, chosen at random, coordinates it. */
    public void start(ExecutionExperimentEntity experiment, ExecutionRunEntity run) {
        if (experiment.getStatus() != ProjectStatus.READY) {
            throw new NotAllowedException("Experiment is not ready");
        }
        if (run.getParticipants().size() < config.federatedLearning().participantsMinAmount()) {
            throw new NotAllowedException("Not enough participants");
        }
        if (run.getSteps().isEmpty()) {
            throw new NotAllowedException("Experiment workflow is missing");
        }
        ExecutionParticipantEntity coordinator = run.getParticipants().get(RANDOM.nextInt(run.getParticipants().size()));
        coordinator.setCoordinator(true);
        experiment.setStatus(ProjectStatus.RUNNING);
        run.begin(RunStatusTypes.RUNNING);
        String globalUniqueId = experiment.getGlobalUniqueId();
        String coordinatorId = coordinator.getParticipantId();
        Boolean modelCanBePublic = experiment.getModelCanBePublic();
        broadcastBO.sendAfterCommit("Start of training " + run.getId(), () -> broadcastBO.startLearning(globalUniqueId, coordinatorId, modelCanBePublic));
    }

    /** All clinics are ready for the step: its relay run is set up and the clinics start it; false when the relay failed. */
    public boolean startStep(ExecutionExperimentEntity experiment, ExecutionRunEntity run, ExecutionRunStepEntity step) {
        Map<String, FederatedLearningRelayInfoDTO> relays;
        try {
            relays = setupRelay(run, step);
        } catch (RuntimeException e) {
            Log.errorf(e, "Relay setup of step %d failed", step.getId());
            return false;
        }
        WorkflowNodeEntity node = step.getWorkflowNode();
        run.getParticipants().forEach(participant -> {
            participant.setCurrentNodeId(node.getNodeId());
            participant.setStepStatus(ProjectStatus.RUNNING);
        });
        step.moveTo(RunStatusTypes.STARTED);
        Log.infof("All clinics ready, starting step %d of training %d", step.getId(), run.getId());
        String globalUniqueId = experiment.getGlobalUniqueId();
        Long nodeId = node.getId();
        String workflowNodeId = node.getNodeId();
        broadcastBO.sendAfterCommit("Start of step " + step.getId(), () -> broadcastBO.startStepLearning(globalUniqueId, nodeId, workflowNodeId, relays));
        return true;
    }

    /** The clinics set up the next step and report once they are ready for it. */
    public void prepareStep(ExecutionExperimentEntity experiment, ExecutionRunEntity run, ExecutionRunStepEntity next) {
        next.setStatus(RunStatusTypes.INITIALIZED);
        WorkflowNodeEntity node = next.getWorkflowNode();
        run.getParticipants().forEach(participant -> {
            participant.setCurrentNodeId(node.getNodeId());
            participant.setStepStatus(ProjectStatus.INIT);
        });
        Log.infof("Setting up step %d of training %d", next.getId(), run.getId());
        String globalUniqueId = experiment.getGlobalUniqueId();
        Long nodeId = node.getId();
        String workflowNodeId = node.getNodeId();
        broadcastBO.sendAfterCommit("Setup of step " + next.getId(), () -> broadcastBO.nextStepLearning(globalUniqueId, nodeId, workflowNodeId));
    }

    // The relay run of the step if its app learns across clinics; the relay data per clinic.
    private Map<String, FederatedLearningRelayInfoDTO> setupRelay(ExecutionRunEntity run, ExecutionRunStepEntity step) {
        FederatedAppVersionEntity appVersion = step.getWorkflowNode().getToolVersion();
        if (appVersion == null) {
            throw new IllegalStateException("Step " + step.getId() + " has no app version");
        }
        if (!appVersion.getFederatedApp().isSupportsFederatedLearning()) {
            Log.infof("App version %d of step %d needs no relay run", appVersion.getId(), step.getId());
            return Collections.emptyMap();
        }
        boolean v1 = Boolean.TRUE.equals(appVersion.getFederatedApp().getOldFCVersion());
        RelayServerAppVersions version = v1 ? RelayServerAppVersions.v1 : RelayServerAppVersions.v2;
        List<ExecutionParticipantEntity> participants = run.getParticipants();
        long coordinators = participants.stream().filter(ExecutionParticipantEntity::isCoordinator).count();
        // v2 has an aggregator slot of its own for the coordinator, v1 lets the first client coordinate.
        int clients = v1 ? participants.size() : Math.max(1, participants.size() - (int) coordinators);
        CreateFLLearningRelayServerResponseDTO response = globalRelayService.setupFL(CreateFLLearningRelayServerRequestDTO.create(clients, v1));

        Deque<FederatedLearningRelayInfoDTO> clientSlots = new ArrayDeque<>();
        FederatedLearningRelayInfoDTO coordinatorSlot = null;
        for (String clientId : response.getClientIds()) {
            FederatedLearningRelayInfoDTO slot = response.relayInfoFor(version, clientId, response.getClientId2ClientKey().get(clientId));
            if (v1 && coordinatorSlot == null) {
                slot.setCoordinator(true);
                coordinatorSlot = slot;
            } else {
                clientSlots.add(slot);
            }
        }
        if (!v1) {
            coordinatorSlot = response.relayInfoFor(version, response.getCoordinatorId(), response.getCoordinatorKey());
            coordinatorSlot.setCoordinator(true);
        }

        Map<String, FederatedLearningRelayInfoDTO> relays = new HashMap<>();
        for (ExecutionParticipantEntity participant : participants) {
            FederatedLearningRelayInfoDTO slot = participant.isCoordinator() ? coordinatorSlot : clientSlots.poll();
            if (slot != null) {
                relays.put(participant.getParticipantId(), slot);
            }
        }

        ExecutionFederationEntity federation = step.federationOrNew();
        federation.setConfig(response.runConfigOf(version));
        federation.setRelayClientIds(relays.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().getId())));
        federation.setRelayStopped(false);
        Log.infof("Relay run of step %d: %d clinics, %d client slots (v1=%b)", step.getId(), participants.size(), clients, v1);
        return relays;
    }
}
