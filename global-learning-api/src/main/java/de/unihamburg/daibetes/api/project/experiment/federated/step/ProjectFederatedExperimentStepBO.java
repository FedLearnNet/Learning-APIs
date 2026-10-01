package de.unihamburg.daibetes.api.project.experiment.federated.step;

import bio.cosy.feddb.core.api.run.RunStatusTypes;
import bio.cosy.feddb.core.api.socket.FederatedLearningRelayInfoDTO;
import bio.cosy.feddb.core.api.workflow.base.step.BaseWorkflowStepBO;
import bio.cosy.feddb.core.services.controller.CreateFLLearningRelayServerRequestDTO;
import bio.cosy.feddb.core.services.controller.CreateFLLearningRelayServerResponseDTO;
import bio.cosy.feddb.core.services.controller.RelayServerAppVersions;
import bio.cosy.feddb.core.services.controller.RelayStopRequestDTO;
import de.unihamburg.daibetes.api.app.version.FederatedAppVersionEntity;
import de.unihamburg.daibetes.api.project.experiment.federated.ProjectFederatedExperimentEntity;
import de.unihamburg.daibetes.api.project.experiment.federated.participants.ProjectFederatedExperimentParticipantAO;
import de.unihamburg.daibetes.api.project.experiment.federated.participants.ProjectFederatedExperimentParticipantEntity;
import de.unihamburg.daibetes.api.workflow.node.WorkflowNodeEntity;
import de.unihamburg.daibetes.services.GlobalRelayService;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;


@ApplicationScoped
public class ProjectFederatedExperimentStepBO extends BaseWorkflowStepBO<ProjectFederatedExperimentStepDTO, ProjectFederatedExperimentStepEntity, ProjectFederatedExperimentStepAO, ProjectFederatedExperimentStepMapper> {

    @Inject
    @RestClient
    GlobalRelayService globalRelayService;

    @Inject
    ProjectFederatedExperimentParticipantAO participantAO;

    public List<ProjectFederatedExperimentStepEntity> createForWorkflow(ProjectFederatedExperimentEntity entity) {
        List<ProjectFederatedExperimentStepEntity> steps = new ArrayList<>();
        for (WorkflowNodeEntity node : entity.getProject().getWorkflow().getNodes()) {
            ProjectFederatedExperimentStepEntity stepEntity = new ProjectFederatedExperimentStepEntity();
            stepEntity.setStepStatus(RunStatusTypes.INITIALIZED);
            stepEntity.setWorkflowNode(node);
            stepEntity.setExperiment(entity);
            ao.persist(stepEntity);
            steps.add(stepEntity);
        }
        return steps;
    }

    public Map<String, FederatedLearningRelayInfoDTO> handleRelaySetup(ProjectFederatedExperimentEntity experiment) {
        WorkflowNodeEntity workflowNode = experiment.getCurrentWorkflowNode().getWorkflowNode();
        FederatedAppVersionEntity appVersion = workflowNode.getFederatedAppVersion();
        if (appVersion == null
                && workflowNode.getSubModel() != null
                && workflowNode.getSubModel().getModelVersion() != null
                && workflowNode.getSubModel().getModelVersion().getModel() != null) {
            appVersion = workflowNode.getSubModel().getModelVersion().getModel().getFederatedAppVersion();
        }
        if (appVersion == null) {
            Log.errorf("Cannot find app version for experiment %d, workflow node %d", experiment.getId(), experiment.getCurrentWorkflowNode().getId());
            throw new IllegalStateException("Cannot find app version for experiment " + experiment.getId() + ", workflow node " + experiment.getCurrentWorkflowNode().getId());
        }
        if (appVersion.getFederatedApp().isSupportsFederatedLearning()) {
            Log.infof("Do need to setup relay server");
            List<FederatedLearningRelayInfoDTO> response = handleRelaySetup(experiment, appVersion);
            Map<String, FederatedLearningRelayInfoDTO> result = new HashMap<>();
            Map<String, String> relayClientIds = new HashMap<>();
            FederatedLearningRelayInfoDTO coordinatorInfo = null;
            for (ProjectFederatedExperimentParticipantEntity participant : experiment.getParticipants()) {
                String id = participant.getUniqueRandomClinicId();
                if (participant.getIsCoordinator()) {
                    if (coordinatorInfo == null) {
                        coordinatorInfo = response.stream().filter(FederatedLearningRelayInfoDTO::getCoordinator).findFirst().orElse(null);
                    }
                    if (coordinatorInfo != null) {
                        result.put(id, coordinatorInfo);
                        relayClientIds.put(id, coordinatorInfo.getId());
                    }
                } else {
                    response.stream().filter(r -> !r.getCoordinator()).findFirst().ifPresent(r -> {
                        result.put(id, r);
                        relayClientIds.put(id, r.getId());
                        response.remove(r);
                    });
                }

            }
            // Remember per step which relay client id belongs to which clinic, see findRelayChannelForClient
            ProjectFederatedExperimentStepEntity step = experiment.getCurrentWorkflowNode();
            step.setRelayClientIds(relayClientIds);
            step.setRelayStopped(false);
            ao.setRelayRunTransactional(step.getId(), step.getChannel(), step.getRelayKey(), relayClientIds);
            return result;

        }

        Log.infof("App version %d does not require a relay server for experiment %d", appVersion.getId(), experiment.getId());
        return Collections.emptyMap();

    }

    /**
     * Checks and manages the initiation of a running state for federated experiment participants.
     * This method verifies that all participants are ready and synchronized before setup the learning step.
     *
     * @param experiment The experiment entity requesting to start running
     * @throws IllegalArgumentException if entity is null or invalid
     * @throws IllegalStateException    if experiment state prevents running
     */
    public List<FederatedLearningRelayInfoDTO> handleRelaySetup(ProjectFederatedExperimentEntity experiment, FederatedAppVersionEntity appVersion) {
        // Broadcast start learning to all participants
        Long currentWorkflowId = experiment.getCurrentWorkflowNode().getId();
        if (appVersion == null) {
            //if null its an model, which doesnt need relay setup
            Log.infof("Current workflow node %s for experiment %d has no app version - skipping relay setup", currentWorkflowId, experiment.getId());
            return new ArrayList<>();
        }
        int nParticipants = experiment.getParticipants().size();
        boolean isV1 = appVersion.getFederatedApp().getOldFCVersion() != null && appVersion.getFederatedApp().getOldFCVersion();
        // v2 has a separate coordinator/aggregator slot in addition to the client slots, and the
        // coordinator clinic is assigned to it. So the number of client slots must exclude the
        // coordinator(s); otherwise the relay/aggregator expects more clients than will ever connect
        // and the round hangs. v1 has no separate slot (the first client is also the coordinator).
        long coordinators = experiment.getParticipants().stream()
                .filter(ProjectFederatedExperimentParticipantEntity::getIsCoordinator)
                .count();
        int relayClientCount = isV1 ? nParticipants : Math.max(1, nParticipants - (int) coordinators);
        Log.infof("Relay setup for experiment %d node %d: %d participant(s), %d coordinator(s) -> %d relay client slot(s) (isV1=%b)",
                experiment.getId(), currentWorkflowId, nParticipants, coordinators, relayClientCount, isV1);
        CreateFLLearningRelayServerRequestDTO startup = CreateFLLearningRelayServerRequestDTO.create(relayClientCount, isV1);

        List<FederatedLearningRelayInfoDTO> responses = new ArrayList<>();

        CreateFLLearningRelayServerResponseDTO response = globalRelayService.setupFL(startup);

        experiment.getCurrentWorkflowNode().setChannel(response.getChannel());
        experiment.getCurrentWorkflowNode().setRelayKey(response.getRelayKey());

        boolean isFirst = true;
        for (String clientId : response.getClientIds()) {
            FederatedLearningRelayInfoDTO relayInfo = mapper.responseToRelayInfo(response, isV1 ? RelayServerAppVersions.v1 : RelayServerAppVersions.v2);
            relayInfo.setKey(response.getClientId2ClientKey().get(clientId));
            relayInfo.setId(clientId);
            // Always set the flag (the mapper leaves it null) so downstream null-unboxing cannot NPE.
            relayInfo.setCoordinator(isV1 && isFirst);
            if (isV1 && isFirst) {
                isFirst = false;
            }
            responses.add(relayInfo);
        }

        if (!isV1) {
            FederatedLearningRelayInfoDTO relayInfo = mapper.responseToRelayInfo(response, RelayServerAppVersions.v2);
            relayInfo.setKey(response.getCoordinatorKey());
            relayInfo.setId(response.getCoordinatorId());
            relayInfo.setCoordinator(true);
            responses.add(relayInfo);
        }

        Log.infof("Relay setup response for experiment %d with channelId %s: %s", experiment.getId(),
                experiment.getChannelId(),
                response.getRelayKey());
        return responses;
    }

    /**
     * Returns the relay channel of the step running the given workflow node, but only if the clinic participates
     * in the experiment and the relay client id is the one assigned to that clinic for this step.
     * Empty if any of these checks fails or the relay run of the step was already stopped.
     */
    @Transactional
    public Optional<String> findRelayChannelForClient(String globalUniqueExperimentId, String nodeId, String uniqueRandomClinicId, String clientId) {
        if (globalUniqueExperimentId == null || nodeId == null || uniqueRandomClinicId == null || clientId == null) {
            return Optional.empty();
        }
        Optional<ProjectFederatedExperimentParticipantEntity> participant = participantAO.findByExperimentIdAndClinicId(globalUniqueExperimentId, uniqueRandomClinicId);
        if (participant.isEmpty()) {
            return Optional.empty();
        }
        Optional<ProjectFederatedExperimentStepEntity> stepOptional = ao.findByWorkflowNodeId(participant.get().getExperiment().getId(), nodeId);
        if (stepOptional.isEmpty()) {
            return Optional.empty();
        }
        ProjectFederatedExperimentStepEntity step = stepOptional.get();
        if (step.getChannel() == null || Boolean.TRUE.equals(step.getRelayStopped()) || step.getRelayClientIds() == null) {
            return Optional.empty();
        }
        if (!clientId.equals(step.getRelayClientIds().get(uniqueRandomClinicId))) {
            return Optional.empty();
        }
        return Optional.of(step.getChannel());
    }

    /**
     * Stops the relay run of every step of the experiment that still has one.
     */
    public void stopRelayRuns(Long experimentId) {
        for (ProjectFederatedExperimentStepEntity step : ao.findWithOpenRelayRun(experimentId)) {
            stopRelayRun(step.getId(), step.getChannel());
        }
    }

    /**
     * Stops the relay run of the step on the relay server, if it has one that was not stopped yet.
     * Never throws: a relay server that cannot be reached must not break the workflow, the run is then
     * only cleaned up when the relay server restarts.
     */
    public void stopRelayRun(Long stepId) {
        ProjectFederatedExperimentStepEntity step = ao.findById(stepId);
        if (step == null || step.getChannel() == null || Boolean.TRUE.equals(step.getRelayStopped())) {
            return;
        }
        stopRelayRun(step.getId(), step.getChannel());
    }

    private void stopRelayRun(Long stepId, String channel) {
        try (Response response = globalRelayService.stopFL(new RelayStopRequestDTO(channel))) {
            if (response.getStatus() != Response.Status.OK.getStatusCode()) {
                Log.warnf("Relay server answered %d when stopping the relay run of step %d", response.getStatus(), stepId);
                return;
            }
            ao.setRelayStoppedTransactional(stepId);
            Log.infof("Stopped relay run of step %d", stepId);
        } catch (Exception e) {
            Log.warnf(e, "Could not stop the relay run of step %d", stepId);
        }
    }
}
