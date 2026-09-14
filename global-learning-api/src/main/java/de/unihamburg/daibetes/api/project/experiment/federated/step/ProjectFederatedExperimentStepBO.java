package de.unihamburg.daibetes.api.project.experiment.federated.step;

import bio.cosy.feddb.core.api.run.RunStatusTypes;
import bio.cosy.feddb.core.api.socket.FederatedLearningRelayInfoDTO;
import bio.cosy.feddb.core.api.workflow.base.step.BaseWorkflowStepBO;
import bio.cosy.feddb.core.services.controller.CreateFLLearningRelayServerRequestDTO;
import bio.cosy.feddb.core.services.controller.CreateFLLearningRelayServerResponseDTO;
import bio.cosy.feddb.core.services.controller.RelayServerAppVersions;
import de.unihamburg.daibetes.api.app.version.FederatedAppVersionEntity;
import de.unihamburg.daibetes.api.project.experiment.federated.ProjectFederatedExperimentEntity;
import de.unihamburg.daibetes.api.project.experiment.federated.participants.ProjectFederatedExperimentParticipantEntity;
import de.unihamburg.daibetes.api.workflow.node.WorkflowNodeEntity;
import de.unihamburg.daibetes.services.GlobalRelayService;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


@ApplicationScoped
public class ProjectFederatedExperimentStepBO extends BaseWorkflowStepBO<ProjectFederatedExperimentStepDTO, ProjectFederatedExperimentStepEntity, ProjectFederatedExperimentStepAO, ProjectFederatedExperimentStepMapper> {

    @Inject
    @RestClient
    GlobalRelayService globalRelayService;

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
            FederatedLearningRelayInfoDTO coordinatorInfo = null;
            for (ProjectFederatedExperimentParticipantEntity participant : experiment.getParticipants()) {
                String id = participant.getUniqueRandomClinicId();
                if (participant.getIsCoordinator()) {
                    if (coordinatorInfo == null) {
                        coordinatorInfo = response.stream().filter(FederatedLearningRelayInfoDTO::getCoordinator).findFirst().orElse(null);
                    }
                    if (coordinatorInfo != null) {
                        result.put(id, coordinatorInfo);
                    }
                } else {
                    response.stream().filter(r -> !r.getCoordinator()).findFirst().ifPresent(r -> {
                        result.put(id, r);
                        response.remove(r);
                    });
                }

            }
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
}
