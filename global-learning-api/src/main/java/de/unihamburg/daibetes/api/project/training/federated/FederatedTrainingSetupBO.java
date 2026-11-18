package de.unihamburg.daibetes.api.project.training.federated;

import bio.cosy.feddb.core.api.tre.ExternalDatabaseAccess;
import bio.cosy.feddb.core.api.project.ProjectDetailDTO;
import bio.cosy.feddb.core.api.project.ProjectStatus;
import bio.cosy.feddb.core.api.socket.FederatedLearningRelayInfoDTO;
import bio.cosy.feddb.core.api.socket.ProjectFederatedExperimentForLocalDTO;
import bio.cosy.feddb.core.api.tre.TreSourceDTO;
import bio.cosy.feddb.core.api.workflow.WorkflowDTO;
import bio.cosy.feddb.core.api.workflow.node.WorkflowNodeDetailDTO;
import bio.cosy.feddb.core.execution.ExecutionLocation;
import bio.cosy.feddb.core.execution.ExecutionMode;
import bio.cosy.feddb.core.execution.ExecutionPurpose;
import bio.cosy.feddb.core.execution.executor.ExecutionStartDTO;
import bio.cosy.feddb.core.execution.helper.NodeHyperParams;
import bio.cosy.feddb.core.execution.run.types.RunStatusTypes;
import bio.cosy.feddb.core.services.controller.CreateFLLearningRelayServerRequestDTO;
import bio.cosy.feddb.core.services.controller.CreateFLLearningRelayServerResponseDTO;
import bio.cosy.feddb.core.services.controller.RelayServerAppVersions;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.unihamburg.daibetes.api.app.version.FederatedAppVersionEntity;
import de.unihamburg.daibetes.api.feddbclient.FLNetClientBroadcastBO;
import de.unihamburg.daibetes.api.project.ProjectAO;
import de.unihamburg.daibetes.api.project.ProjectBO;
import de.unihamburg.daibetes.api.query.response.QueryResponseBO;
import de.unihamburg.daibetes.api.workflow.WorkflowBO;
import de.unihamburg.daibetes.api.workflow.WorkflowEntity;
import de.unihamburg.daibetes.api.workflow.node.WorkflowNodeEntity;
import de.unihamburg.daibetes.config.FLNetConfig;
import de.unihamburg.daibetes.execution.attempt.ExecutionAttemptAO;
import de.unihamburg.daibetes.execution.attempt.ExecutionAttemptEntity;
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
import jakarta.ws.rs.BadRequestException;
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
    ExecutionAttemptAO attemptAO;

    @Inject
    FederatedTrainingAggregatorBO aggregatorBO;

    @Inject
    FederatedTrainingFlowerAggregatorBO flowerAggregatorBO;

    @Inject
    QueryResponseBO queryResponseBO;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    @RestClient
    GlobalRelayService globalRelayService;

    @ConfigProperty(name = "quarkus.rest-client.relay-api.url", defaultValue = "Default Relay Server")
    String relayServerAddress;

    /** A training on a copy of the project's workflow; the clinics are asked to accept it. */
    public ExecutionRunEntity create(Long projectId, ExecutionMode mode, CreateProjectExperimentDTO create, String keycloakId, Set<String> roles) {
        String connectionId = null;
        if (mode == ExecutionMode.TRE) {
            // TRE trains on the chosen clinic, which coordinates itself
            create.setPlatformIsCoordinator(false);
        }
        projectBO.checkPlatformIsCoordinatorAllowed(Boolean.TRUE.equals(create.getPlatformIsCoordinator()));
        ProjectDetailDTO project = projectBO.getById(projectId);
        TreSourceDTO treSource = project.getTreSource();
        if (treSource != null && mode != ExecutionMode.TRE) {
            throw new BadRequestException("A project on an external database only runs as TRE");
        }
        if (mode == ExecutionMode.TRE) {
            checkTreWorkflow(workflowBO.getById(project.getWorkflowId()), treSource);
        }
        // on an external database every client is asked, only the one holding it answers
        if (mode == ExecutionMode.TRE && treSource == null) {
            if (create.getQueryResponseId() == null) {
                throw new BadRequestException("A TRE training needs a client");
            }
            connectionId = queryResponseBO.findConnectionId(create.getQueryResponseId(), project.getQueryId());
        }
        WorkflowEntity workflow = workflowBO.cloneWorkflow(workflowBO.getById(project.getWorkflowId()));
        if (!Boolean.TRUE.equals(create.getPlatformIsCoordinator()) && workflowHasFlowerNode(workflow)) {
            // Flower's aggregator (Tool/SuperLink bundle) needs its Fleet API port reachable by clinics'
            // SuperNodes; only the platform is set up to expose a port like that.
            throw new NotAllowedException("This workflow contains a Flower-based tool, which requires the "
                    + "platform to be the aggregator; random-clinic aggregation is not supported for it.");
        }
        project.setWorkflowId(workflow.getId());

        ExecutionExperimentEntity experiment = experimentMapper.federatedCreateDtoToEntity(create, keycloakId, projectAO.findById(projectId),
                project, workflow, relayServerAddress, HashUtil.randomHex(32));
        experimentAO.persist(experiment);

        ExecutionStartDTO start = new ExecutionStartDTO(ExecutionPurpose.TRAINING, mode, create.getName());
        start.setLocation(ExecutionLocation.LOCAL);
        ExecutionRunEntity run = workflowExecutionBO.createTransactional(start, workflow.getNodes());
        run.setKeycloakId(keycloakId);
        run.setExperiment(experiment);
        experiment.getRuns().add(run);
        Log.infof("Created federated training %d of project %d", run.getId(), projectId);

        ProjectFederatedExperimentForLocalDTO query = experimentMapper.experimentToLearningQuery(experiment, roles, workflowBO.entityToDto(workflow));
        query.setMode(mode);
        String target = connectionId;
        broadcastBO.sendAfterCommit("Learning query of training " + run.getId(), () -> broadcastBO.fireLearningQuery(query, target));
        return run;
    }

    /**
     * A clinic accepted the training with its number of patients; false when it does not count. Once enough clinics did,
     * it is ready to start. A clinic may report patients in several parts when its cohorts decide on their own, it then
     * takes part once.
     */
    public boolean accept(ExecutionExperimentEntity experiment, ExecutionRunEntity run, Integer count, String clinicId, Boolean modelCanBePublic) {
        String globalUniqueId = experiment.getGlobalUniqueId();
        // an external database has no patient count
        boolean external = experiment.getProjectVersion() != null && experiment.getProjectVersion().getTreSource() != null;
        int minCount = external ? Integer.MIN_VALUE : config.federatedLearning().participantsMinDataCount();
        if ((count == null || count <= 0) && minCount < 0 && !external) {
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
        if (participant.isEmpty() && run.getMode() == ExecutionMode.TRE && !run.getParticipants().isEmpty()) {
            Log.warnf("Ignoring clinic %s for TRE training %s, another clinic takes part", clinicId, globalUniqueId);
            return false;
        }
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
                && experiment.getAcceptanceClinicCount() >= minParticipants(run)) {
            experiment.setStatus(ProjectStatus.READY);
        }
        Log.infof("Clinic %s accepted training %s, %d patients of %d clinics now", clinicId, globalUniqueId,
                experiment.getAcceptanceCount(), experiment.getAcceptanceClinicCount());
        return true;
    }

    /** Starts a training enough clinics accepted; the platform or one of them, chosen at random, coordinates it. */
    public void start(ExecutionExperimentEntity experiment, ExecutionRunEntity run) {
        if (experiment.getStatus() != ProjectStatus.READY) {
            throw new NotAllowedException("Experiment is not ready");
        }
        if (run.getParticipants().size() < minParticipants(run)) {
            throw new NotAllowedException("Not enough participants");
        }
        if (run.getSteps().isEmpty()) {
            throw new NotAllowedException("Experiment workflow is missing");
        }
        // The clinics accepted knowing who aggregates, a clinic must not take over silently when the platform cannot.
        if (experiment.isPlatformIsCoordinator() && !projectBO.isPlatformAggregatorSupported()) {
            throw new NotAllowedException("Platform aggregator support is disabled on this deployment; "
                    + "cannot start this experiment with the platform as coordinator.");
        }
        String coordinatorId = experiment.isPlatformIsCoordinator() ? null : chooseClinicCoordinator(run);
        experiment.setStatus(ProjectStatus.RUNNING);
        run.begin(RunStatusTypes.RUNNING);
        String globalUniqueId = experiment.getGlobalUniqueId();
        Boolean modelCanBePublic = experiment.getModelCanBePublic();
        broadcastBO.sendAfterCommit("Start of training " + run.getId(), () -> broadcastBO.startLearning(globalUniqueId, coordinatorId, modelCanBePublic));
    }

    /** All clinics are ready for the step: its relay run is set up and the clinics start it; false when the relay failed. */
    public boolean startStep(ExecutionExperimentEntity experiment, ExecutionRunEntity run, ExecutionRunStepEntity step) {
        RelaySetup relay;
        try {
            relay = run.getMode() == ExecutionMode.TRE ? new RelaySetup(withoutRelay(run), null) : setupRelay(experiment, run, step);
        } catch (RuntimeException e) {
            Log.errorf(e, "Relay setup of step %d failed", step.getId());
            return false;
        }
        WorkflowNodeEntity node = step.getWorkflowNode();
        run.getParticipants().forEach(participant -> {
            participant.setCurrentNodeId(node.getNodeId());
            participant.setStepStatus(ProjectStatus.RUNNING);
        });
        addAttempts(run, step, relay.platform());
        step.moveTo(RunStatusTypes.STARTED);
        Log.infof("All clinics ready, starting step %d of training %d", step.getId(), run.getId());
        String globalUniqueId = experiment.getGlobalUniqueId();
        Long nodeId = node.getId();
        String workflowNodeId = node.getNodeId();
        Map<String, FederatedLearningRelayInfoDTO> relays = relay.clinics();
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

    // TRE or an app without federated learning: every clinic still gets its start, just without a relay.
    private Map<String, FederatedLearningRelayInfoDTO> withoutRelay(ExecutionRunEntity run) {
        Map<String, FederatedLearningRelayInfoDTO> clinics = new HashMap<>();
        run.getParticipants().forEach(participant -> clinics.put(participant.getParticipantId(), null));
        return clinics;
    }

    // Every clinic gets the platform's SuperLink address instead of a relay slot; the aggregator
    // (always the platform for a Flower step, see FederatedTrainingFlowerAggregatorBO) needs none.
    private Map<String, FederatedLearningRelayInfoDTO> flowerClinicSlots(ExecutionRunEntity run, ExecutionRunStepEntity step) {
        String superlinkAddress = config.federatedLearning().flower().superlinkAddress()
                .orElseThrow(() -> new IllegalStateException(
                        "flnet.federated-learning.flower.superlink-address is not configured; step " + step.getId()
                                + " uses a Flower tool and needs it to reach clinics"));
        Map<String, FederatedLearningRelayInfoDTO> clinics = new HashMap<>();
        run.getParticipants().forEach(participant -> {
            FederatedLearningRelayInfoDTO slot = new FederatedLearningRelayInfoDTO();
            slot.setSuperlinkAddress(superlinkAddress);
            clinics.put(participant.getParticipantId(), slot);
        });
        return clinics;
    }

    private boolean workflowHasFlowerNode(WorkflowEntity workflow) {
        return workflow.getNodes().stream().anyMatch(FederatedTrainingFlowerAggregatorBO::isFlowerNode);
    }

    private void checkTreWorkflow(WorkflowDTO workflow, TreSourceDTO treSource) {
        for (WorkflowNodeDetailDTO node : workflow.getNodes()) {
            if (node.needsFederatedLearningAccess()) {
                throw new BadRequestException("Federated tools can not run in a TRE: " + node.getNodeId());
            }
            if (treSource != null && treSource.getAccess() == ExternalDatabaseAccess.TOOL
                    && !Objects.equals(treSource.getToolVersionId(), node.getFederatedAppVersionId())) {
                throw new BadRequestException("Only " + treSource.getToolName() + " may run on " + treSource.getName());
            }
        }
    }

    private int minParticipants(ExecutionRunEntity run) {
        return run.getMode() == ExecutionMode.TRE ? 1 : config.federatedLearning().participantsMinAmount();
    }

    private String chooseClinicCoordinator(ExecutionRunEntity run) {
        ExecutionParticipantEntity coordinator = run.getParticipants().get(RANDOM.nextInt(run.getParticipants().size()));
        coordinator.setCoordinator(true);
        return coordinator.getParticipantId();
    }

    // An attempt per clinic, and one for the platform's aggregator if it coordinates.
    private void addAttempts(ExecutionRunEntity run, ExecutionRunStepEntity step, FederatedLearningRelayInfoDTO platformSlot) {
        if (!step.getAttempts().isEmpty()) {
            return;
        }
        LinkedHashMap<String, Object> params = NodeHyperParams.of(objectMapper, step.getWorkflowNode());
        step.federationOrNew().setTotalRounds(NodeHyperParams.totalRounds(params));
        for (ExecutionParticipantEntity participant : run.getParticipants()) {
            ExecutionAttemptEntity attempt = new ExecutionAttemptEntity();
            attempt.setPublicId(UUID.randomUUID());
            attempt.setStep(step);
            attempt.setTrialIndex(step.getAttempts().size());
            attempt.setParams(new LinkedHashMap<>(params));
            attempt.begin(RunStatusTypes.RUNNING);
            attempt.federationOrNew().setParticipant(participant);
            step.getAttempts().add(attempt);
            attemptAO.persist(attempt);
            if (participant.isCoordinator()) {
                step.setSelectedAttempt(attempt);
            }
        }
        if (FederatedTrainingFlowerAggregatorBO.isFlowerStep(step)) {
            // Ticket-#2 validation at creation already guarantees the platform coordinates whenever the
            // workflow has a Flower node, so this is unconditional - there is no relay slot for Flower at all.
            flowerAggregatorBO.addAttempt(step, params);
        } else if (platformSlot != null) {
            aggregatorBO.addAttempt(step, platformSlot, params);
        }
    }

    // Relay slot per clinic, and the coordinator slot for the platform's aggregator if it coordinates.
    private record RelaySetup(Map<String, FederatedLearningRelayInfoDTO> clinics, FederatedLearningRelayInfoDTO platform) {
    }

    // The relay run of the step if its app learns across clinics.
    private RelaySetup setupRelay(ExecutionExperimentEntity experiment, ExecutionRunEntity run, ExecutionRunStepEntity step) {
        FederatedAppVersionEntity appVersion = step.getWorkflowNode().getToolVersion();
        if (appVersion == null) {
            throw new IllegalStateException("Step " + step.getId() + " has no app version");
        }
        if (FederatedTrainingFlowerAggregatorBO.isFlowerStep(step)) {
            // Flower's SuperLink is reached directly via its own exposed port, not the FL-Net relay - a Flower
            // app also has isSupportsFederatedLearning() set, so this has to be checked first, not folded into
            // the check below. No relay slot at all, for the platform or for clinics.
            Log.infof("App version %d of step %d uses Flower, no FL-Net relay run needed", appVersion.getId(), step.getId());
            return new RelaySetup(flowerClinicSlots(run, step), null);
        }
        if (!appVersion.getFederatedApp().isSupportsFederatedLearning()) {
            Log.infof("App version %d of step %d needs no relay run", appVersion.getId(), step.getId());
            return new RelaySetup(withoutRelay(run), null);
        }
        boolean v1 = Boolean.TRUE.equals(appVersion.getFederatedApp().getOldFCVersion());
        RelayServerAppVersions version = v1 ? RelayServerAppVersions.v1 : RelayServerAppVersions.v2;
        List<ExecutionParticipantEntity> participants = run.getParticipants();
        boolean platform = experiment.isPlatformIsCoordinator();
        long coordinators = participants.stream().filter(ExecutionParticipantEntity::isCoordinator).count();
        // v2 has an aggregator slot of its own for the coordinator, v1 lets the first client coordinate; a platform
        // coordinating v1 needs that first client on top of the clinics.
        int clients = v1 ? participants.size() + (platform ? 1 : 0) : Math.max(1, participants.size() - (int) coordinators);
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
        if (platform && coordinatorSlot == null) {
            throw new IllegalStateException("Relay server returned no coordinator slot for the aggregator of step " + step.getId());
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
        Log.infof("Relay run of step %d: %d clinics, %d client slots (v1=%b, platform coordinates=%b)",
                step.getId(), participants.size(), clients, v1, platform);
        return new RelaySetup(relays, platform ? coordinatorSlot : null);
    }
}
