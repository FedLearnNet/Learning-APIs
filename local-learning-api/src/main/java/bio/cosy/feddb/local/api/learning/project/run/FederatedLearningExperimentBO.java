package bio.cosy.feddb.local.api.learning.project.run;

import bio.cosy.feddb.core.api.app.config.ToolInputConfigDTO;
import bio.cosy.feddb.core.api.project.ProjectStatus;
import bio.cosy.feddb.core.api.run.RunStatusTypes;
import bio.cosy.feddb.core.api.run.FederatedRunConfigDTO;
import bio.cosy.feddb.core.api.run.FederatedRunParticipantDTO;
import bio.cosy.feddb.core.api.run.StartRunDTO;
import bio.cosy.feddb.core.api.socket.FederatedLearningRelayInfoDTO;
import bio.cosy.feddb.core.api.workflow.WorkflowDTO;
import bio.cosy.feddb.core.api.workflow.base.experiment.BaseWorkflowExperimentBO;
import bio.cosy.feddb.core.api.workflow.node.WorkflowNodeDetailDTO;
import bio.cosy.feddb.core.services.controller.ControllerStartLearningRequestDTO;
import bio.cosy.feddb.core.services.orch.WorkflowOrchestrator;
import bio.cosy.feddb.core.services.orch.dto.StartWorkflowNodeDTO;
import bio.cosy.feddb.core.security.ToolApiKeyService;
import bio.cosy.feddb.core.security.Scope;
import bio.cosy.feddb.local.api.cohort.patient.export.PatientDataExportBO;
import bio.cosy.feddb.local.api.file.FileEntity;
import bio.cosy.feddb.local.api.learning.project.run.data.FederatedLearningExperimentStepDataBO;
import bio.cosy.feddb.local.api.learning.project.run.step.FederatedLearningExperimentStepBO;
import bio.cosy.feddb.local.api.learning.project.run.step.FederatedLearningExperimentStepDTO;
import bio.cosy.feddb.local.api.learning.project.run.step.FederatedLearningExperimentStepEntity;
import bio.cosy.feddb.local.api.learning.request.FederatedLearningRequestEntity;
import bio.cosy.feddb.local.api.workflow.WorkflowBO;
import bio.cosy.feddb.local.api.workflow.node.HyperParamConfigToYml;
import bio.cosy.feddb.local.services.controller.LocalControllerLearningService;
import bio.cosy.feddb.local.services.orch.WorkflowOrchestratorBO;
import com.fasterxml.jackson.core.JsonProcessingException;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.OnOverflow;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.nio.file.Path;
import java.util.*;

import static bio.cosy.feddb.local.api.learning.project.run.FederatedLearningExperimentServiceImpl.EXPERIMENT_CHANNEL;

@ApplicationScoped
public class FederatedLearningExperimentBO
        extends BaseWorkflowExperimentBO<FederatedLearningExperimentDTO,
        FederatedLearningExperimentStepEntity,
        FederatedLearningExperimentStepDTO,
        FederatedLearningExperimentEntity,
        FederatedLearningExperimentAO,
        FederatedLearningExperimentMapper,
        FederatedLearningExperimentStepBO> {

    @Inject
    FederatedLearningExperimentStepBO stepBO;

    @Inject
    FederatedLearningExperimentStepDataBO stepResultBO;

    @Inject
    WorkflowOrchestratorBO workflowOrchestratorBO;

    @Inject
    WorkflowBO workflowBO;

    @Inject
    PatientDataExportBO exportBO;

    @Inject
    @Channel(EXPERIMENT_CHANNEL)
    @OnOverflow(OnOverflow.Strategy.DROP)
    Emitter<FederatedLearningExperimentDTO> infoEmitter;

    @Inject
    FederatedLearningExperimentBroadcastBO federatedLearningExperimentBroadcastBO;

    @Inject
    @RestClient
    LocalControllerLearningService controllerService;

    public FederatedLearningExperimentDTO ensureApprovedLearning(FederatedLearningRequestEntity entity) {
        return ao.getByGlobalRequestId(entity.getGlobalFLExperimentUniqueId())
                .map(mapper::entityToDto)
                .orElseGet(() -> approveLearning(entity));
    }

    public FederatedLearningExperimentDTO approveLearning(FederatedLearningRequestEntity entity) {
        Optional<FederatedLearningExperimentEntity> existing =
                ao.getByGlobalRequestId(entity.getGlobalFLExperimentUniqueId());
        if (existing.isPresent()) {
            return mapper.entityToDto(existing.get());
        }

        FederatedLearningExperimentEntity experiment = new FederatedLearningExperimentEntity();
        experiment.setProject(entity.getProject());
        experiment.setUniqueRandomClinicId(UUID.randomUUID().toString());
        experiment.setExperimentStatus(ProjectStatus.READY);
        ao.persist(experiment);

        Set<FederatedLearningExperimentStepEntity> steps = stepBO.createSteps(experiment);
        experiment.setSteps(steps);

        return mapper.entityToDto(experiment);
    }

    public FederatedLearningExperimentDTO entityToDTO(FederatedLearningExperimentEntity entity) {
        FederatedLearningExperimentDTO dto = mapper.entityToDto(entity);
        List<FederatedLearningExperimentStepDTO> steps = getSteps(entity);
        dto.setSteps(steps);
        return dto;
    }

    public FederatedLearningExperimentDTO findById(Long id) {
        return ao.findByIdOptional(id)
                .map(mapper::entityToDto)
                .orElse(null);
    }

    public List<FederatedLearningExperimentStepDTO> getSteps(FederatedLearningExperimentEntity experiment) {
        return Optional.ofNullable(experiment)
                .map(FederatedLearningExperimentEntity::getSteps)
                .map(e -> e.stream()
                        .map(s -> stepBO.mapStepToDto(s))
                        .toList())
                .orElse(new ArrayList<>());
    }

    public void handleStartLearning(Long experimentId, FederatedLearningRelayInfoDTO relayInfo) {
        FederatedLearningExperimentEntity experiment = ao.findById(experimentId);
        if (experiment == null) {
            throw new IllegalArgumentException("Experiment with id " + experimentId + " not found");
        }
        if (relayInfo != null) {
            // Set in-memory for the immediate controller registration / app start below, and persist
            // transactionally - this method runs outside a transaction, so the entity mutation alone
            // would never be flushed and later reads of the step's relay info would find null.
            experiment.getCurrentWorkflowNode().setRelayInfo(relayInfo);
            stepBO.setRelayInfo(experiment.getCurrentWorkflowNode().getId(), relayInfo);
        }
        handleStartLearning(experiment);
    }

    public ProjectStatus startLearning(Long experimentId, boolean firstStep, FederatedLearningRelayInfoDTO relayInfo) {
        FederatedLearningExperimentStartContext context = prepareStartContext(experimentId, relayInfo);
        if (context == null) {
            Log.warnf("No workflow node available to start for experiment ID: %d", experimentId);
            FederatedLearningExperimentEntity experiment = ao.findById(experimentId);
            return experiment != null ? experiment.getExperimentStatus() : ProjectStatus.ERROR;
        }

        Log.infof("Starting workflow execution for experiment ID: %d", experimentId);
        try {
            String containerId = getWorkflowOrchestrator().executeWorkflow(context.getStartWorkflowDTO(), getWSBaseUrl());
            Log.infof("Started container for experiment ID: %d, container ID: %s", experimentId, containerId);

            if (context.isOldFCVersion()) {
                stepBO.persistStep(context.getStepId(), RunStatusTypes.INITIALIZED, containerId);
            } else {
                stepBO.persistStep(context.getStepId(), RunStatusTypes.PENDING, containerId);
            }
            emitChange(experimentId);

            if (firstStep) {
                uploadData(context.getNode(), experimentId);
            }

            if (context.isOldFCVersion()) {
                try {
                    if (context.getNodeHyperParams() != null && !context.getNodeHyperParams().isEmpty()) {
                        Log.info("Uploading hyperparameters to volume for old FC version");
                        String config = HyperParamConfigToYml.createYaml(context.getNodeHyperParams());
                        workflowOrchestratorBO.uploadConfigToVolume(experimentId, context.getStepId(), config);
                    }
                } catch (JsonProcessingException e) {
                    Log.error("Failed to convert hyperparameters to YAML, so no config uploaded", e);
                }
                return ProjectStatus.READY;
            }
            return ProjectStatus.INIT;
        } catch (Exception e) {
            Log.errorf(e, "Error starting workflow execution for federated experiment ID: %d", experimentId);
            onStepErrorTransactional(experimentId, context.getStepId(), "Error starting workflow execution: " + e.getMessage());
            return ProjectStatus.ERROR;
        }
    }

    public ProjectStatus startLearning(FederatedLearningExperimentEntity experimentEntity, boolean firstStep) {
        return startLearning(experimentEntity.getId(), firstStep, null);
    }


    private FederatedLearningExperimentStartContext prepareStartContext(Long experimentId, FederatedLearningRelayInfoDTO relayInfo) {
        FederatedLearningExperimentEntity experiment = ao.findById(experimentId);
        if (experiment == null) {
            Log.errorf("Experiment not found for learning request ID: %d", experimentId);
            throw new IllegalArgumentException("Experiment not found for learning request ID: " + experimentId);
        }

        WorkflowDTO workflow = getWorkflowDTO(experiment);
        if (workflow == null) {
            Log.errorf("Workflow not found for experiment ID %d", experimentId);
            throw new IllegalArgumentException("Workflow not found for experiment ID: " + experimentId);
        }

        FederatedLearningExperimentStepEntity previousStep = experiment.getCurrentWorkflowNode();
        WorkflowNodeDetailDTO node;
        if (previousStep == null) {
            node = baseWorkflowEngine.firstNode(workflow);
            if (node == null) {
                Log.errorf("No starting node found in workflow for experiment ID %d", experimentId);
                throw new IllegalArgumentException("No starting node found in workflow for experiment ID: " + experimentId);
            }
            ao.markExperimentRunning(experimentId);
        } else {
            String previousNodeId = previousStep.getWorkflowNode().getNodeId();
            node = baseWorkflowEngine.nextNode(workflow, previousNodeId);
            if (node == null) {
                Log.infof("No next node found in workflow for experiment ID %d after node %s", experimentId, previousNodeId);
                return null;
            }
        }

        Optional<FederatedLearningExperimentStepEntity> nextStepOptional = stepBO.findByWorkflowNodeId(experimentId, node.getNodeId());
        if (nextStepOptional.isEmpty()) {
            Log.errorf("Workflow step not found for experimentId %d and nodeId %s", experimentId, node.getNodeId());
            throw new IllegalArgumentException("Workflow step not found for experimentId " + experimentId + " and nodeId " + node.getNodeId());
        }

        FederatedLearningExperimentStepEntity nextStep = nextStepOptional.get();
        if (relayInfo != null) stepBO.setRelayInfo(nextStep.getId(), relayInfo);

        if (!RunStatusTypes.canBeStartedStatus(nextStep.getStepStatus())) {
            Log.errorf("Step with id %d for experimentId %d and nodeId %s is in status %s which is not startable",
                    nextStep.getId(), experimentId, node.getNodeId(), nextStep.getStepStatus());
            throw new IllegalArgumentException("Step with id " + nextStep.getId() + " for experimentId " + experimentId + " and nodeId " + node.getNodeId() + " is in status " + nextStep.getStepStatus() + " which is not startable");
        }

        ao.setCurrentWorkflowNode(experimentId, nextStep);
        if (previousStep == null || baseWorkflowEngine.hasInputNode(workflow, node)) {
            saveFirstStepData(experiment, nextStep);
        }

        String apiKey = toolApiKeyService.issue(Scope.LEARNING_RUN, nextStep.getId());
        StartWorkflowNodeDTO startWorkflowDTO = baseWorkflowEngine.getStartDTO(node, apiKey);
        baseWorkflowEngine.customizeStart(startWorkflowDTO, experimentId, node.getExecutionOrder(), nextStep.getId());
        if (previousStep == null) {
            startWorkflowDTO.setIsFistNode(true);
        } else {
            setInputActions(startWorkflowDTO, node, workflow);
            linkData(previousStep, nextStep, startWorkflowDTO.getInputs());
        }
        customizeStartWorkflowDto(startWorkflowDTO);

        return new FederatedLearningExperimentStartContext(
                nextStep.getId(),
                node,
                startWorkflowDTO,
                node.isOldFCVersion(),
                node.getHyperParams()
        );
    }

    private void uploadData(WorkflowNodeDetailDTO currentNode, Long experimentId) {
        try {
            FederatedLearningExperimentEntity entity = ao.findById(experimentId);
            if (entity == null) {
                Log.errorf("Learning experiment not found while uploading data for experiment ID: %d", experimentId);
                return;
            }
            Optional<FederatedLearningExperimentStepEntity> currentStep = stepBO.findByWorkflowNodeId(experimentId, currentNode.getNodeId());
            if (currentStep.isEmpty()) {
                Log.errorf("Current workflow step not found while uploading data for experiment ID: %d and node %s",
                        experimentId, currentNode.getNodeId());
                return;
            }
            List<ToolInputConfigDTO> appInputConfigs = currentNode.getAppInputConfig();
            Log.info("Starting data export for learning workflow ID: " + entity.getId());
            Map<String, Path> data = getDataV1(appInputConfigs, entity);
            if (data.isEmpty()) {
                Log.warn("No data found to upload for learning.");
                return;
            }
            Log.info("Uploading data for learning: " + data.size() + " files found.");
            for (Map.Entry<String, Path> entry : data.entrySet()) {
                Integer executionOrder = currentStep.get().getWorkflowNode().getExecutionOrder();
                Log.info("Uploading file: " + entry.getKey() + " to workflow ID: " + entity.getId() + ", node: " + executionOrder);
                workflowOrchestratorBO.uploadFilesToVolume(entity.getId(), Long.valueOf(executionOrder), entry.getValue(), entry.getKey());
            }
        } catch (Exception e) {
            Log.errorf(e, "Error during data export for learning experiment ID: %d", experimentId);
        }
    }


    public FederatedLearningExperimentMapper getMapper() {
        return mapper;
    }

    @Override
    protected WorkflowOrchestrator getWorkflowOrchestrator() {
        return workflowOrchestratorBO;
    }

    @Override
    protected WorkflowDTO getWorkflowDTO(Long experimentId) {
        FederatedLearningExperimentEntity experimentEntity = ao.findById(experimentId);
        return getWorkflowDTO(experimentEntity);
    }

    @Override
    protected Scope getToolApiKeyScope() {
        return Scope.LEARNING_RUN;
    }

    @Override
    protected String getWSBaseUrl() {
        return "learning/run";
    }

    @Override
    protected void emitChange(FederatedLearningExperimentDTO dto) {
        try {
            infoEmitter.send(dto);
            Log.debug("SSE event sent: " + dto);
        } catch (Exception e) {
            Log.debug("Attempted to send SSE event, but client connection was already closed.", e);
        }
    }

    @Override
    protected void startStep(FederatedLearningExperimentStepEntity entity) {
        StartRunDTO run = getStartup(entity.getId());
        if (run.isSupportFederatedLearning()) {
            enrichWithFederatedRelay(run, entity);
        }
        federatedLearningExperimentBroadcastBO.startStep(run);
    }

    /**
     * Populates the relay/participant configuration the app needs for a federated round. Without this
     * the app receives an empty participants list and aborts with "No participants configured".
     * This clinic always participates as a CLIENT; the coordinator clinic additionally runs the
     * AGGREGATOR (and sets startAggregator).
     */
    private void enrichWithFederatedRelay(StartRunDTO run, FederatedLearningExperimentStepEntity entity) {
        FederatedLearningRelayInfoDTO relay = entity.getRelayInfo();
        if (relay == null) {
            Log.warnf("No relay info on step %d - app will receive no participants and the round cannot start", entity.getId());
            return;
        }
        boolean isCoordinator = relay.getCoordinator();

        // Send exactly ONE participant: this clinic. For a real run the app (pyfedappwrap) identifies
        // its own instance either by participantId == system_settings.app_id, or by receiving a single
        // participant. All clinics share the same app_id, so only the single-participant path is
        // reliable - sending a second (aggregator) participant breaks self-identification with
        // "Real federated runs require ... only one participant".
        FederatedRunParticipantDTO client = new FederatedRunParticipantDTO();
        client.setParticipantId(relay.getId());
        client.setRole(isCoordinator ? "AGGREGATOR" : "CLIENT");
        client.setHyperParams(run.getHyperParams());
        client.setInputFilePaths(run.getInputFilePaths());
        run.setParticipants(List.of(client));
        run.setStartAggregator(isCoordinator);
        run.setTotalRounds(extractTotalRounds(run.getHyperParams()));

        FederatedRunConfigDTO config = new FederatedRunConfigDTO();
        config.setChannel(relay.getChannel());
        config.setClientId(relay.getId());
        config.setClientKey(relay.getKey());
        config.setRelayKey(relay.getRelayKey());
        config.setCoordinatorId(relay.getCoordinatorId());
        config.setMaxNumClients(relay.getMaxNumClients());
        config.setOrderClientIds(relay.getOrderClientIds() == null ? null : new ArrayList<>(relay.getOrderClientIds()));
        config.setAppVersion(relay.getAppVersion() != null ? relay.getAppVersion().name() : null);
        run.setConfig(config);

        Log.infof("Federated run for step %d: participant %s role=%s coordinator=%b channel=%s",
                entity.getId(), relay.getId(), client.getRole(), isCoordinator, relay.getChannel());
    }

    private Integer extractTotalRounds(Map<String, Object> hyperParams) {
        if (hyperParams == null) {
            return null;
        }
        Object value = hyperParams.getOrDefault("federated_rounds", hyperParams.get("total_rounds"));
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value != null) {
            try {
                return Integer.parseInt(value.toString());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    @Override
    public void onPreFinishStep(FederatedLearningExperimentStepDTO step) {
        stepResultBO.saveResults(step.getId());
    }

    @Override
    public void onFinishStep(Long experimentId, boolean experimentFinish) {
        if (!experimentFinish) {
            startLearning(experimentId, false, null);
        }
    }

    @Override
    protected WorkflowDTO getWorkflowDTO(FederatedLearningExperimentEntity experimentEntity) {
        return workflowBO.entityToDto(experimentEntity.getProject().getWorkflow());
    }

    @Override
    public void customizeStartWorkflowDto(StartWorkflowNodeDTO startWorkflow) {
        // Enable client-side result upload (sets ENABLE_REMOTE_RESULT_SAVING=true in the app container
        // via orch-api). The app then POSTs its outputs to this clinic's local-learning-api /output
        // endpoint and the trained model to /model via upload_model_file. Each clinic persists its own
        // results locally; the /model handler forwards to global only for the coordinator.
        startWorkflow.setEnableRemoteResultSaving(true);
    }

    @Override
    protected void saveFirstStepData(FederatedLearningExperimentEntity entity, FederatedLearningExperimentStepEntity stepEntity) {
        Path inputPath = getInputPath(entity);
        if (inputPath != null) {
            stepResultBO.createForStep(inputPath.toFile(), stepEntity.getId());
        }
    }

    @Override
    public Path getInputPath(FederatedLearningExperimentEntity entity) {
        Long requestId = entity.getProject().getRequest().getId();
        Long queryId = entity.getProject().getQuery().getId();
        return exportBO.exportDataForLearning(entity.getProject().getExportConfig(), queryId, requestId);
    }

    @Override
    public void handleStartFederatedLearningFC(FederatedLearningExperimentStepEntity step) {
        Long runId = step.getId();
        if (step.getRelayInfo() == null) {
            Log.error("No relay info found for runId " + runId);
            setAppHasError(step, "No relay info found");
            return;
        }
        Log.info("Old FeatureCloud version detected for runId " + runId + ". Please update to the latest version. " +
                "Handling start learning for old FC version, step: " + step.getId() + ". S" +
                "tep details: " + step + ". Channel id is: " + step.getRelayInfo().getChannel());
        ControllerStartLearningRequestDTO startRequest = mapper.relayToController(step.getRelayInfo(), runId.toString());
        Log.info("Start learning request send to controller: " + startRequest);
        try (Response response = controllerService.startLearning(startRequest)) {
            // Response handled automatically by try-with-resource // response empty if 200
            if (response.getStatus() != Response.Status.OK.getStatusCode()) {
                Log.error("Failed to start learning for runId " + runId + ": " + response.readEntity(String.class));
                setAppHasError(step, "Failed to start learning: " + response.readEntity(String.class));
                throw new RuntimeException("Failed to start learning: " + response.readEntity(String.class));
            }
        } catch (Exception e) {
            Log.error("Error starting learning for runId " + runId, e);
            setAppHasError(step, "Error starting learning: " + e.getMessage());
            throw new RuntimeException("Error starting learning: " + e.getMessage(), e);
        }

    }

    @Override
    public FileEntity getInputFile(FederatedLearningExperimentEntity entity) {
        //always return something non-null to indicate that input data is present
        return new FileEntity();
    }
}
