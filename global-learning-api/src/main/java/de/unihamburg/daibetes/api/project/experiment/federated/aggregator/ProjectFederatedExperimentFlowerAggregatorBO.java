package de.unihamburg.daibetes.api.project.experiment.federated.aggregator;

import bio.cosy.feddb.core.api.project.ProjectStatus;
import bio.cosy.feddb.core.api.run.RunStatusTypes;
import bio.cosy.feddb.core.api.run.StartRunDTO;
import bio.cosy.feddb.core.api.workflow.WorkflowDTO;
import bio.cosy.feddb.core.api.workflow.base.BaseWorkflowEngine;
import bio.cosy.feddb.core.api.workflow.node.WorkflowNodeDetailDTO;
import bio.cosy.feddb.core.security.Scope;
import bio.cosy.feddb.core.security.ToolApiKeyService;
import bio.cosy.feddb.core.services.orch.dto.StartWorkflowNodeDTO;
import de.unihamburg.daibetes.api.project.experiment.federated.ProjectFederatedExperimentBO;
import de.unihamburg.daibetes.api.project.experiment.federated.ProjectFederatedExperimentEntity;
import de.unihamburg.daibetes.api.project.experiment.federated.step.ProjectFederatedExperimentStepBO;
import de.unihamburg.daibetes.api.project.experiment.federated.step.ProjectFederatedExperimentStepEntity;
import de.unihamburg.daibetes.api.workflow.WorkflowBO;
import de.unihamburg.daibetes.services.WorkflowOrchestratorBO;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.util.LinkedHashMap;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Starts/stops the platform's own Flower SuperLink container, for experiments whose current
 * workflow node is Flower-tagged ({@link WorkflowNodeDetailDTO#isFlowerCommunicator()}). Mirrors
 * {@link ProjectFederatedExperimentAggregatorBO}'s container-lifecycle shape, but needs none of
 * that class's FL-Net-specific CSR/Controller-sidecar dance or Go Relay Server credentials -
 * Flower's SuperLink is reached directly via its own exposed Fleet API port (see
 * Flower_Integration_Plan.md's resolved architecture decisions for #13), and clinic SuperNodes
 * authenticate to it at the application layer, independent of this platform's relay.
 */
@ApplicationScoped
public class ProjectFederatedExperimentFlowerAggregatorBO {

    /** Becomes the container's WS_URL env var; the path the SuperLink's own federated worker
     *  manager (Python-Tool-API#11) dials back to with its start acknowledgement and status
     *  reports, over the same generic app-message protocol FL-Net tools already use. */
    public static final String WS_BASE_URL = "project/experiment/federated/flower-aggregator";

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

    protected static final BaseWorkflowEngine baseWorkflowEngine = new BaseWorkflowEngine();

    // Steps whose container the platform stops itself: their connection close is expected and must not be
    // treated as a lost aggregator (stop() runs inside a transaction whose step progress is not committed yet).
    private final Set<Long> stoppedSteps = ConcurrentHashMap.newKeySet();

    /**
     * Starts the platform's own Flower SuperLink container for the experiment's current step.
     *
     * @throws IllegalStateException if orch-api rejects the start request - callers should treat
     *                                this the same as a relay-setup failure (stop the round).
     */
    public void start(ProjectFederatedExperimentEntity experiment, WorkflowNodeDetailDTO node) {
        Long stepId = experiment.getCurrentWorkflowNode().getId();
        String apiKey = toolApiKeyService.issue(Scope.FEDERATED_AGGREGATOR_RUN, stepId);

        StartWorkflowNodeDTO startWorkflowDTO = baseWorkflowEngine.getStartDTO(node, apiKey);
        baseWorkflowEngine.customizeStart(startWorkflowDTO, experiment.getId(), node.getExecutionOrder(), stepId);

        String containerId;
        try {
            containerId = workflowOrchestratorBO.executeWorkflow(startWorkflowDTO, WS_BASE_URL);
        } catch (Exception e) {
            toolApiKeyService.revoke(Scope.FEDERATED_AGGREGATOR_RUN, stepId);
            throw e;
        }
        stepBO.setFlowerAggregatorContainer(stepId, containerId);
        Log.infof("Started platform Flower aggregator container %s for experiment %d step %d",
                containerId, experiment.getId(), stepId);
    }

    /**
     * Stops the platform Flower aggregator container for the given step, if one was started. Safe
     * to call unconditionally - a no-op when there's no container to stop.
     */
    public void stop(Long stepId) {
        if (stepId == null) {
            return;
        }
        Optional<ProjectFederatedExperimentStepEntity> stepOptional = stepBO.findEntityByIdOptional(stepId);
        if (stepOptional.isEmpty() || stepOptional.get().getContainerId() == null) {
            return;
        }
        stoppedSteps.add(stepId);
        toolApiKeyService.revoke(Scope.FEDERATED_AGGREGATOR_RUN, stepId);
        try {
            workflowOrchestratorBO.cleanup(stepOptional.get().getContainerId(), true);
            Log.infof("Stopped platform Flower aggregator container %s for step %d",
                    stepOptional.get().getContainerId(), stepId);
        } catch (Exception e) {
            Log.errorf(e, "Failed to stop platform Flower aggregator container %s for step %d: %s",
                    stepOptional.get().getContainerId(), stepId, e.getMessage());
        }
    }

    /**
     * Rebuilds the StartRunDTO for the WS endpoint's onOpen handshake. No relay enrichment here:
     * Flower needs none of FL-Net's relay-channel/client-id config on this DTO.
     */
    @Transactional
    public StartRunDTO getStartup(Long stepId) {
        ProjectFederatedExperimentStepEntity step = stepBO.findEntityByIdOptional(stepId)
                .orElseThrow(() -> new IllegalArgumentException("Step with id " + stepId + " not found"));
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
        run.setStartAggregator(true);
        run.setAggregatorOnly(true);
        return run;
    }

    /**
     * Feeds a status report from the platform's own Flower aggregator container into the
     * experiment's lifecycle. Mirrors {@link ProjectFederatedExperimentAggregatorBO#onStatusUpdate}.
     */
    public void onStatusUpdate(Long stepId, RunStatusTypes status, String error) {
        if (status == RunStatusTypes.FINISHED) {
            experimentBO.onPlatformAggregatorFinished(stepId);
            return;
        }
        if (status != RunStatusTypes.ERROR) {
            return;
        }
        stepBO.findEntityByIdOptional(stepId).ifPresentOrElse(step -> {
            Log.errorf("Platform Flower aggregator reported error for experiment %d step %d: %s",
                    step.getExperiment().getId(), stepId, error);
            experimentBO.stopLearning(step.getExperiment(), ProjectStatus.ERROR);
        }, () -> Log.errorf("Platform Flower aggregator reported error for unknown step %d: %s", stepId, error));
    }

    /**
     * The aggregator's API key is revoked when its connection closes, so it cannot report anymore: a
     * connection that closes before FINISHED while its step is still running fails the experiment
     * instead of letting it wait for the aggregator forever.
     */
    @Transactional
    public void onConnectionClosed(Long stepId) {
        if (stoppedSteps.remove(stepId)) {
            return;
        }
        stepBO.findEntityByIdOptional(stepId).ifPresent(step -> {
            ProjectFederatedExperimentEntity experiment = step.getExperiment();
            boolean stepStillRunning = ProjectStatus.RUNNING.equals(experiment.getExperimentStatus())
                    && experiment.getCurrentWorkflowNode() != null
                    && stepId.equals(experiment.getCurrentWorkflowNode().getId());
            if (stepStillRunning && step.getContainerId() != null && !step.isPlatformAggregatorFinished()) {
                onStatusUpdate(stepId, RunStatusTypes.ERROR, "Platform Flower aggregator disconnected before finishing the step");
            }
        });
    }
}
