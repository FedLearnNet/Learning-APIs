package de.unihamburg.daibetes.api.project.experiment.federated.aggregator;

import bio.cosy.feddb.core.api.project.ProjectStatus;
import bio.cosy.feddb.core.api.run.FederatedRunEnricher;
import bio.cosy.feddb.core.api.run.RunStatusTypes;
import bio.cosy.feddb.core.api.run.StartRunDTO;
import bio.cosy.feddb.core.api.socket.FederatedLearningRelayInfoDTO;
import bio.cosy.feddb.core.api.workflow.WorkflowDTO;
import bio.cosy.feddb.core.api.workflow.base.BaseWorkflowEngine;
import bio.cosy.feddb.core.api.workflow.node.WorkflowNodeDetailDTO;
import bio.cosy.feddb.core.security.Scope;
import bio.cosy.feddb.core.security.ToolApiKeyService;
import bio.cosy.feddb.core.services.controller.ControllerStartLearningRequestDTO;
import bio.cosy.feddb.core.services.controller.ControllerStartLearningResponseDTO;
import bio.cosy.feddb.core.services.controller.ControllerStartRelayingRequestDTO;
import bio.cosy.feddb.core.services.controller.RelaySignCertRequestDTO;
import bio.cosy.feddb.core.services.controller.RelaySignCertResponseDTO;
import bio.cosy.feddb.core.services.orch.dto.StartWorkflowNodeDTO;
import de.unihamburg.daibetes.api.project.experiment.federated.ProjectFederatedExperimentBO;
import de.unihamburg.daibetes.api.project.experiment.federated.ProjectFederatedExperimentEntity;
import de.unihamburg.daibetes.api.project.experiment.federated.step.ProjectFederatedExperimentStepBO;
import de.unihamburg.daibetes.api.project.experiment.federated.step.ProjectFederatedExperimentStepEntity;
import de.unihamburg.daibetes.api.workflow.WorkflowBO;
import de.unihamburg.daibetes.services.GlobalRelayService;
import de.unihamburg.daibetes.services.LocalControllerLearningService;
import de.unihamburg.daibetes.services.WorkflowOrchestratorBO;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Optional;

/**
 * Starts/stops the FL aggregator on the platform itself (global-learning-api's own orch-api +
 * Controller sidecar), for experiments where platformIsCoordinator is active. Deliberately not a
 * {@code BaseWorkflowExperimentBO} subclass - that base class assumes one experiment drives
 * exactly one container end-to-end, but a federated experiment already drives N clinic
 * participants via {@link ProjectFederatedExperimentBO}; this bolts one more (platform-only)
 * container onto that existing round-by-round flow instead of forcing the generic single-container
 * lifecycle to fit a multi-participant shape.
 */
@ApplicationScoped
public class ProjectFederatedExperimentAggregatorBO {

    /** Becomes the container's WS_URL env var (see orch-api ContainerNetworkEnv) and the path the
     *  platform's own Controller sidecar dials back to. */
    public static final String WS_BASE_URL = "project/experiment/federated/aggregator";

    @Inject
    WorkflowOrchestratorBO workflowOrchestratorBO;

    @Inject
    ToolApiKeyService toolApiKeyService;

    @Inject
    ProjectFederatedExperimentStepBO stepBO;

    @Inject
    WorkflowBO workflowBO;

    @Inject
    ProjectFederatedExperimentBO experimentBO;

    @Inject
    @RestClient
    LocalControllerLearningService controllerService;

    @Inject
    @RestClient
    GlobalRelayService globalRelayService;

    protected static final BaseWorkflowEngine baseWorkflowEngine = new BaseWorkflowEngine();

    /**
     * Starts the platform's own aggregator container for the experiment's current step.
     *
     * @throws IllegalStateException if orch-api rejects the start request - callers should treat
     *                                this the same as a relay-setup failure (stop the round).
     */
    public void start(ProjectFederatedExperimentEntity experiment, WorkflowNodeDetailDTO node, FederatedLearningRelayInfoDTO platformRelayInfo) {
        Long stepId = experiment.getCurrentWorkflowNode().getId();
        String apiKey = toolApiKeyService.issue(Scope.FEDERATED_AGGREGATOR_RUN, stepId);

        // Persist relay credentials before the container can possibly connect, so a container that
        // dials in immediately after orch-api accepts the start request never races the DB write
        // that getStartup() depends on.
        stepBO.setPlatformAggregatorContainer(stepId, null, platformRelayInfo);

        try {
            startControllerLearning(platformRelayInfo, stepId);
        } catch (Exception e) {
            toolApiKeyService.revoke(Scope.FEDERATED_AGGREGATOR_RUN, stepId);
            throw e;
        }

        StartWorkflowNodeDTO startWorkflowDTO = baseWorkflowEngine.getStartDTO(node, apiKey);
        baseWorkflowEngine.customizeStart(startWorkflowDTO, experiment.getId(), node.getExecutionOrder(), stepId);

        String containerId;
        try {
            containerId = workflowOrchestratorBO.executeWorkflow(startWorkflowDTO, WS_BASE_URL);
        } catch (Exception e) {
            toolApiKeyService.revoke(Scope.FEDERATED_AGGREGATOR_RUN, stepId);
            throw e;
        }
        stepBO.setPlatformAggregatorContainer(stepId, containerId, platformRelayInfo);
        Log.infof("Started platform aggregator container %s for experiment %d step %d",
                containerId, experiment.getId(), stepId);
    }

    /**
     * Registers the run with the platform's own Controller sidecar, signs its CSR via the relay
     * server (in-process - the platform already is the global server, unlike a clinic it does not
     * need to round-trip the CSR through a websocket), and passes the signed certificate back to
     * the Controller so it can connect to the relay.
     */
    private void startControllerLearning(FederatedLearningRelayInfoDTO relay, Long stepId) {
        ControllerStartLearningRequestDTO startRequest = new ControllerStartLearningRequestDTO(
                relay.getChannel(), relay.getId(), relay.getKey(), relay.getRelayKey(),
                stepId.toString(), relay.getCoordinatorId(), relay.getMaxNumClients(),
                relay.getOrderClientIds() == null ? null : new ArrayList<>(relay.getOrderClientIds()),
                relay.getId(), relay.getAppVersion() != null ? relay.getAppVersion().name() : null);

        String csr;
        try (Response response = controllerService.startLearning(startRequest)) {
            if (response.getStatus() != Response.Status.OK.getStatusCode()) {
                throw new IllegalStateException("controller answered " + response.getStatus()
                        + ": " + response.readEntity(String.class));
            }
            ControllerStartLearningResponseDTO started = response.readEntity(ControllerStartLearningResponseDTO.class);
            csr = started != null ? started.getCsr() : null;
            if (csr == null || csr.isBlank()) {
                throw new IllegalStateException("controller returned no certificate signing request");
            }
        }

        RelaySignCertResponseDTO signed = globalRelayService.signCert(
                new RelaySignCertRequestDTO(relay.getChannel(), relay.getId(), csr));
        if (signed == null || signed.getCertificate() == null || signed.getCertificate().isBlank()) {
            throw new IllegalStateException("relay server returned no certificate for step " + stepId);
        }

        try (Response relayingResponse = controllerService.startRelaying(
                new ControllerStartRelayingRequestDTO(relay.getChannel(), relay.getId(), signed.getCertificate()))) {
            if (relayingResponse.getStatus() != Response.Status.OK.getStatusCode()) {
                throw new IllegalStateException("controller answered " + relayingResponse.getStatus()
                        + " on start-relaying: " + relayingResponse.readEntity(String.class));
            }
        }
    }

    /**
     * Stops the platform aggregator container for the given step, if one was started. Safe to
     * call unconditionally (e.g. on every stopLearning/handleNextStep) - a no-op when there's no
     * container to stop.
     */
    public void stop(Long stepId) {
        if (stepId == null) {
            return;
        }
        Optional<ProjectFederatedExperimentStepEntity> stepOptional = stepBO.findEntityByIdOptional(stepId);
        if (stepOptional.isEmpty() || stepOptional.get().getContainerId() == null) {
            return;
        }
        toolApiKeyService.revoke(Scope.FEDERATED_AGGREGATOR_RUN, stepId);
        try {
            workflowOrchestratorBO.cleanup(stepOptional.get().getContainerId(), true);
            Log.infof("Stopped platform aggregator container %s for step %d", stepOptional.get().getContainerId(), stepId);
        } catch (Exception e) {
            Log.errorf(e, "Failed to stop platform aggregator container %s for step %d: %s",
                    stepOptional.get().getContainerId(), stepId, e.getMessage());
        }
    }

    /**
     * Rebuilds the StartRunDTO for the WS endpoint's onOpen handshake. {@code @OnOpen} callbacks
     * run outside any ambient transaction/session, so this needs its own boundary to safely
     * traverse step -&gt; experiment -&gt; workflow and step -&gt; workflowNode.
     */
    @Transactional
    public StartRunDTO getStartup(Long stepId) {
        ProjectFederatedExperimentStepEntity step = stepBO.findEntityByIdOptional(stepId)
                .orElseThrow(() -> new IllegalArgumentException("Step with id " + stepId + " not found"));
        FederatedLearningRelayInfoDTO relay = step.getPlatformRelayInfo();
        if (relay == null) {
            throw new IllegalStateException("No platform relay info persisted for step " + stepId);
        }
        WorkflowDTO workflow = workflowBO.entityToDto(step.getExperiment().getWorkflow());
        WorkflowNodeDetailDTO node = baseWorkflowEngine.findByNodeId(workflow, step.getWorkflowNode().getNodeId());

        StartRunDTO run = new StartRunDTO();
        run.setId(stepId);
        run.setSupportFederatedLearning(true);
        run.setTrainable(node.getIsTrainable());
        run.setHyperParams(node.getHyperParams());
        // The platform never holds patient data - the aggregator-only role needs none.
        run.setInputFilePaths(new LinkedHashMap<>());
        run.setStatus(RunStatusTypes.PENDING);
        FederatedRunEnricher.enrichWithFederatedRelay(run, relay, true);
        return run;
    }

    /**
     * Feeds a status report from the platform's own aggregator container into the experiment's
     * lifecycle. Only ERROR is actionable here - clinics (now all CLIENT-only in platform mode)
     * already drive round completion via their own participant status updates, so a healthy
     * aggregator reporting RUNNING/FINISHED needs no action.
     */
    public void onStatusUpdate(Long stepId, RunStatusTypes status, String error) {
        if (status != RunStatusTypes.ERROR) {
            return;
        }
        stepBO.findEntityByIdOptional(stepId).ifPresentOrElse(step -> {
            Log.errorf("Platform aggregator reported error for experiment %d step %d: %s",
                    step.getExperiment().getId(), stepId, error);
            experimentBO.stopLearning(step.getExperiment(), ProjectStatus.ERROR);
        }, () -> Log.errorf("Platform aggregator reported error for unknown step %d: %s", stepId, error));
    }
}
