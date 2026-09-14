package bio.cosy.feddb.core.api.workflow.base.experiment;

import bio.cosy.feddb.core.api.app.config.ToolInputConfigDTO;
import bio.cosy.feddb.core.api.project.ProjectStatus;
import bio.cosy.feddb.core.api.run.FinishRunDTO;
import bio.cosy.feddb.core.api.run.RunStatusTypes;
import bio.cosy.feddb.core.api.run.StartRunDTO;
import bio.cosy.feddb.core.api.run.UpdateRunDTO;
import bio.cosy.feddb.core.api.workflow.WorkflowDTO;
import bio.cosy.feddb.core.api.workflow.base.BaseWorkflowEngine;
import bio.cosy.feddb.core.api.workflow.base.WorkflowException;
import bio.cosy.feddb.core.api.workflow.base.step.BaseWorkflowStepBO;
import bio.cosy.feddb.core.api.workflow.base.step.BaseWorkflowStepDTO;
import bio.cosy.feddb.core.api.workflow.base.step.BaseWorkflowStepEntity;
import bio.cosy.feddb.core.api.workflow.node.WorkflowNodeDetailDTO;
import bio.cosy.feddb.core.base.BaseBo;
import bio.cosy.feddb.core.base.BaseFileEntity;
import bio.cosy.feddb.core.base.BaseMapper;
import bio.cosy.feddb.core.services.orch.WorkflowOrchestrator;
import bio.cosy.feddb.core.services.orch.dto.StartWorkflowNodeDTO;
import bio.cosy.feddb.core.services.orch.dto.WorkflowNodeInputActionDTO;
import bio.cosy.feddb.core.security.ToolApiKeyService;
import bio.cosy.feddb.core.security.Scope;
import io.quarkus.logging.Log;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.nio.file.Path;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public abstract class BaseWorkflowExperimentBO<Dto extends BaseWorkflowExperimentDTO,
        StepEntity extends BaseWorkflowStepEntity<?, ?>,
        StepDTO extends BaseWorkflowStepDTO,
        Entity extends BaseWorkflowExperimentEntity<StepEntity>,
        Ao extends BaseWorkflowExperimentAO<Entity>,
        Mapper extends BaseMapper<Dto, Entity>,
        StepBO extends BaseWorkflowStepBO<StepDTO, StepEntity, ?, ?>>
        extends BaseBo<Dto, Entity, Ao, Mapper> {

    @Inject
    public StepBO stepBo;

    @Inject
    protected ToolApiKeyService toolApiKeyService;

    protected static final BaseWorkflowEngine baseWorkflowEngine = new BaseWorkflowEngine();

    @Transactional
    protected BaseWorkflowExperimentInitContext prepareInitLearning(Long experimentId) {

        Optional<Entity> experimentOptional = ao.findByIdOptional(experimentId);

        if (experimentOptional.isEmpty()) {
            Log.errorf("Experiment not found for learning request ID: %d", experimentId);
            throw new WorkflowException("Experiment not found for learning request ID: " + experimentId);
        }
        Entity experiment = experimentOptional.get();
        WorkflowDTO workflow = getWorkflowDTO(experimentId);
        if (workflow == null) {
            Log.errorf("Workflow not found for experiment ID %d", experimentId);
            throw new WorkflowException("Workflow not found for experiment ID: " + experimentId);
        }

        StepEntity prevStep = experiment.getCurrentWorkflowNode();
        WorkflowNodeDetailDTO node;
        if (prevStep == null) {
            node = baseWorkflowEngine.firstNode(workflow);
            if (node == null) {
                Log.errorf("No starting node found in workflow for experiment ID %d", experimentId);
                throw new WorkflowException("No starting node found in workflow for experiment ID: " + experimentId);
            }
            ao.markExperimentRunning(experimentId);
        } else {
            String prevNodeId = prevStep.getWorkflowNode().getNodeId();
            node = baseWorkflowEngine.nextNode(workflow, prevNodeId);
            if (node == null) {
                Log.infof("No next node found in workflow for experiment ID %d after node %s", experimentId, prevNodeId);
                return null;
            }
        }
        if (node.getNodeId() == null) {
            throw new WorkflowException("No node found in workflow for experiment ID " + experimentId);
        }
        Optional<StepEntity> stepOptional = stepBo.findByWorkflowNodeId(experimentId, node.getNodeId());

        if (stepOptional.isEmpty()) {
            Log.errorf("WorkflowNode not found for experimentId %d and nodeId %s", experimentId, node.getNodeId());
            throw new WorkflowException("WorkflowNode not found for experimentId " + experimentId + " and nodeId " + node.getNodeId());
        }
        if (!RunStatusTypes.canBeStartedStatus(stepOptional.get().getStepStatus())) {
            Log.errorf("Step with id %d for experimentId %d and nodeId %s is in status %s which is not startable",
                    stepOptional.get().getId(), experimentId, node.getNodeId(), stepOptional.get().getStepStatus());
            throw new WorkflowException("Step with id " + stepOptional.get().getId() + " for experimentId " + experimentId + " and nodeId " + node.getNodeId() + " is in status " + stepOptional.get().getStepStatus() + " which is not startable");
        }
        ao.setCurrentWorkflowNode(experimentId, stepOptional.get());
        boolean isOldFCVersion = node.isOldFCVersion();

        StepEntity step = stepOptional.get();
        if (prevStep == null || baseWorkflowEngine.hasInputNode(workflow, node)) {
            saveFirstStepData(experiment, step);
        }
        String apiKey = toolApiKeyService.issue(getToolApiKeyScope(), step.getId());
        StartWorkflowNodeDTO startWorkflowDTO = baseWorkflowEngine.getStartDTO(node, apiKey);
        baseWorkflowEngine.customizeStart(startWorkflowDTO, experimentId, node.getExecutionOrder(), step.getId());
        if (prevStep == null) {
            startWorkflowDTO.setIsFistNode(true);
        } else {
            setInputActions(startWorkflowDTO, node, workflow);
            linkData(prevStep, step, startWorkflowDTO.getInputs());
        }
        customizeStartWorkflowDto(startWorkflowDTO);
        return new BaseWorkflowExperimentInitContext(startWorkflowDTO,
                isOldFCVersion,
                step.getId(),
                node.getHyperParams());
    }

    public boolean handleInitLearning(Long experimentId) {
        Log.infof("Received init learning request: %d", experimentId);

        BaseWorkflowExperimentInitContext context = prepareInitLearning(experimentId);
        Log.infof("Starting workflow execution for experiment ID: %d", experimentId);
        try {
            String containerId = getWorkflowOrchestrator().executeWorkflow(context.getStartDTO(), getWSBaseUrl());
            Log.infof("Started first container for experimentId ID: %d, container ID: %s",
                    experimentId, containerId);
            if (context.isOldFCVersion()) {
                // Handle old FeatureCloud version, this app will not connect to the ws, so we set the status to READY directly
                if (context.hyperparamsAreSet()) {
                    uploadConfigToVolume(context.getNodeHyperParams(), context.getStartDTO());
                }
                stepBo.persistStep(context.getStepId(), RunStatusTypes.INITIALIZED, containerId);
            } else {
                stepBo.persistStep(context.getStepId(), RunStatusTypes.PENDING, containerId);
            }
            emitChangeTransactional(experimentId);
        } catch (Exception e) {
            Log.errorf(e,"Error starting workflow execution for experiment ID: %d", experimentId);
            onStepErrorTransactional(experimentId, context.getStepId(), "Error starting workflow execution: " + e.getMessage());
            return false;
        }
        Log.infof("Successfully started learning workflow for experiment ID: %d", experimentId);
        return true;
    }

    @Transactional
    public void onStepErrorTransactional(Long experimentId, Long stepId, String message) {
        cleanup(experimentId);
        stepBo.persistStepError(stepId, message);
        ao.markExperimentError(experimentId);
        emitChange(getById(experimentId));
    }

    public void handleStartLearning(Entity experiment) {
        StepEntity currentStep = experiment.getCurrentWorkflowNode();
        Long experimentId = experiment.getId();
        if (currentStep == null) {
            Log.errorf("No current workflow step found for experiment ID: %d", experimentId);
            throw new WorkflowException("No current workflow step found for experiment ID: " + experimentId);
        }
        Log.infof("Starting execution step for learning experiment ID: %d", experiment.getId());
        try {
            if (currentStep.getWorkflowNode().isOldFCVersion()) {
                handleStartFederatedLearningFC(currentStep);
            } else {
                if (currentStep.getWorkflowNode().isSupportsFederatedLearning()) {
                    handleStartFederatedLearningFC(currentStep);
                }
                startStep(currentStep);
            }
        } catch (Exception e) {
            Log.error("Error starting learning for experiment " + experimentId, e);
            setAppHasError(currentStep, "Error starting learning: " + e.getMessage());
            throw new WorkflowException("Error starting learning: " + e.getMessage(), e);
        }
        emitChange(mapper.entityToDto(experiment));
    }


    public StepDTO updateStatus(Long stepId, UpdateRunDTO updateTest) {
        StepDTO dto = stepBo.getById(stepId);
        dto.setStepStatus(updateTest.getStatus());
        dto.setLastError(updateTest.getError());
        dto.setProgress(updateTest.getProgress());
        return updateStatus(dto);
    }

    public StepDTO updateStatus(Long stepId, FinishRunDTO finishTest) {
        StepDTO dto = stepBo.getById(stepId);
        dto.setStepStatus(RunStatusTypes.FINISHED);
        // Persist the wrapper-measured run metadata (timings) reported on finish.
        dto.setMeta(finishTest.getMeta());
        return updateStatus(dto);
    }

    public StepDTO updateStatus(Long stepId, StartRunDTO startRun) {
        StepDTO dto = stepBo.getById(stepId);
        dto.setStepStatus(RunStatusTypes.RUNNING);
        return updateStatus(dto);
    }

    /**
     * Updates the status of a federated learning experiment step and handles any error conditions.
     *
     * @param dto The data transfer object containing the updated step status and related information
     * @return The updated experiment step DTO after persistence
     * @throws IllegalArgumentException if the step entity with the given ID is not found
     */
    public StepDTO updateStatus(StepDTO dto) {
        Long experimentId = dto.getExperimentId();
        Log.infof("Updating status for experiment step ID: %d", dto.getId());
        Entity experiment = ao.findById(experimentId);
        if (experiment == null) {
            Log.errorf("Experiment not found for ID: %d", experimentId);
            throw new IllegalArgumentException("Experiment with id " + experimentId + " not found");
        }
        if (ProjectStatus.isPreRunning(experiment.getExperimentStatus())) {
            ao.markExperimentError(experimentId);
        }
        String nodeId = experiment.getCurrentWorkflowNode().getWorkflowNode().getNodeId();

        WorkflowDTO workflow = getWorkflowDTO(experimentId);
        StepDTO updatedStepDTO = stepBo.updateStatus(dto);
        // Update status and handle any errors
        if (dto.getLastError() != null) {
            ao.updateStatusTransactional(experimentId, ProjectStatus.ERROR);
            onErrorStep(dto);
            Log.warnf("Error detected for experiment ID %d: %s", experimentId, dto.getLastError());
            emitChange(mapper.entityToDto(experiment));
            return updatedStepDTO;
        }

        if (RunStatusTypes.isFinalStatus(updatedStepDTO.getStepStatus())) {
            boolean isLastStep = baseWorkflowEngine.isLastStep(workflow, nodeId);
            Log.infof("Final status detected for step ID: %d, is last step: %b", dto.getId(), isLastStep);
            onPreFinishStep(updatedStepDTO);
            finishRun(experiment, isLastStep, updatedStepDTO);
        }

        onUpdateStep(experiment, updatedStepDTO);

        Log.infof("Successfully updated status for step ID: %d", dto.getId());
        emitChange(mapper.entityToDto(experiment));
        return updatedStepDTO;
    }

    private void finishRun(Entity experiment, boolean isLastStep, StepDTO updatedStepDTO) {
        boolean overAllFinish = false;
        if (isLastStep && RunStatusTypes.FINISHED.equals(updatedStepDTO.getStepStatus())) {
            Log.infof("Finishing run for experiment ID: %d", experiment.getId());
            ao.markExperimentFinish(experiment.getId());
            overAllFinish = true;
            Log.infof("Cleaning up workflow resources for project ID: %d", experiment.getId());
            cleanup(experiment.getId());
        } else {
            Log.infof("Stop container: %s", updatedStepDTO.getContainerId());
            cleanup(updatedStepDTO.getContainerId(), false);
        }

        // Cleanup workflow resources
        onFinishStep(experiment.getId(), overAllFinish);
        Log.infof("Successfully stopped learning workflow for request ID: %d", experiment.getId());
        emitChange(mapper.entityToDto(experiment));
    }

    /**
     * Stops a federated learning workflow and cleans up associated resources.
     *
     * @param experimentId The stop request containing the workflowId ID to terminate
     */
    public Dto stopLearning(Long experimentId) {
        Log.infof("Received stop workflowId for ID: %d", experimentId);

        Entity experiment = ao.findById(experimentId);
        if (experiment == null) {
            Log.errorf("Experiment not found for ID: %d", experimentId);
            throw new IllegalArgumentException("Experiment with id " + experimentId + " not found");
        }
        ao.markExperimentStop(experimentId);
        experiment.getSteps().forEach(step -> {
            if (!RunStatusTypes.isFinalStatus(step.getStepStatus())) {
                stepBo.persistStep(step.getId(), RunStatusTypes.STOPPED);
            }
        });
        // Cleanup workflow resources
        Log.infof("Cleaning up workflow resources for ID: %d", experimentId);
        cleanup(experimentId);

        Log.infof("Successfully stopped learning workflow for ID: %d", experimentId);
        emitChange(experimentId);
        return getById(experimentId);
    }

    protected void setAppHasError(StepEntity step, String errorMessage) {
        onStepErrorTransactional(step.getExperiment().getId(), step.getId(), errorMessage);
    }

    @SuppressWarnings("unchecked")
    public StartRunDTO getStartup(Long stepId) {
        Log.infof("Starting for stepId: %d", stepId);
        Optional<StepEntity> stepOptional = stepBo.findEntityByIdOptional(stepId);
        if (stepOptional.isEmpty()) {
            throw new IllegalArgumentException("Step with id " + stepId + " not found");
        }
        StepEntity stepEntity = stepOptional.get();
        Long experimentId = stepEntity.getExperiment().getId();
        WorkflowDTO workflow = getWorkflowDTO(experimentId);
        WorkflowNodeDetailDTO node = baseWorkflowEngine.findByNodeId(workflow, stepEntity.getWorkflowNode().getNodeId());
        StartRunDTO testDto = new StartRunDTO();
        testDto.setId(stepId);
        testDto.setInputData(null);
        testDto.setSupportFederatedLearning(node.getSupportsFederatedLearning());
        testDto.setTrainable(node.getIsTrainable());
        testDto.setInputFilePaths(getDataV2((Entity) stepEntity.getExperiment(), node.getAppInputConfig()));
        testDto.setStatus(RunStatusTypes.PENDING);
        testDto.setHyperParams(node.getHyperParams());
        return testDto;
    }

    public Map<String, Path> getDataV1(Entity entity) {
        List<ToolInputConfigDTO> config = getCurrentInputs(entity);
        return getDataV1(config, entity);
    }

    public Map<String, Path> getDataV1(List<ToolInputConfigDTO> config, Entity entity) {
        if (config.isEmpty()) {
            return new HashMap<>();
        }
        Path tempFile = getInputPath(entity);
        if (tempFile != null) {

            return config.stream()
                    .map(this::getFileName)
                    .collect(Collectors.toMap(
                            input -> input,
                            input -> tempFile
                    ));
        }
        return new HashMap<>();
    }

    public LinkedHashMap<String, String> getDataV2(Entity entity) {
        List<ToolInputConfigDTO> config = getCurrentInputs(entity);
        return getDataV2(entity, config);
    }

    public LinkedHashMap<String, String> getDataV2(Entity entity, List<ToolInputConfigDTO> config) {
        if (config.isEmpty()) {
            return new LinkedHashMap<>();
        }
        BaseFileEntity tempFile = getInputFile(entity);
        if (tempFile != null) {

            return config.stream()
                    .collect(Collectors.toMap(
                            ToolInputConfigDTO::getVariableName,
                            this::getFileName,
                            (e1, e2) -> e1,
                            LinkedHashMap::new
                    ));
        }
        return new LinkedHashMap<>();
    }

    public void cleanup(Long experimentId) {
        Log.infof("Starting cleanup for experimentId: %d", experimentId);
        getWorkflowOrchestrator().cleanup(experimentId);
    }

    public void cleanup(String containerId, boolean cleanup) {
        Log.infof("Starting cleanup for containerId: %s", containerId);
        getWorkflowOrchestrator().cleanup(containerId, cleanup);
    }


    @Transactional
    public void emitChangeTransactional(Long experimentId) {
        emitChange(experimentId);
    }


    public void emitChange(Long experimentId) {
        Entity experiment = ao.findById(experimentId);
        emitChange(mapper.entityToDto(experiment));
    }

    private String getFileName(ToolInputConfigDTO input) {
        return input.getVariableName() + "." + input.getType().name().toLowerCase();
    }


    public void setInputActions(StartWorkflowNodeDTO dto, WorkflowNodeDetailDTO node, WorkflowDTO workflow) {

        final Map<String, WorkflowNodeDetailDTO> byId = Optional.ofNullable(workflow.getNodes())
                .orElseGet(List::of).stream()
                .filter(n -> n.getNodeId() != null)
                .collect(Collectors.toMap(WorkflowNodeDetailDTO::getNodeId, Function.identity(), (a, b) -> a));


        List<WorkflowNodeInputActionDTO> actions = Optional.ofNullable(workflow.getConnections())
                .orElseGet(List::of).stream()
                .filter(c -> node.getNodeId().equals(c.getInputNodeId()))
                .distinct()
                .map(c -> {
                    WorkflowNodeInputActionDTO action = new WorkflowNodeInputActionDTO();
                    WorkflowNodeDetailDTO inputNode = byId.get(c.getOutputNodeId());
                    if (inputNode == null) {
                        Log.infof("[setInputActions] Input node with id %s not found", c.getInputNodeId());
                        return null;
                    }
                    Optional<StepEntity> stepOptional = stepBo.findByWorkflowNodeId(dto.getWorkflowId(),
                            inputNode.getNodeId());
                    if (stepOptional.isEmpty()) {
                        Log.infof("[setInputActions] Step with id %s not found in experiment &d",
                                inputNode.getNodeId(), dto.getWorkflowId());
                        return null;
                    }
                    action.setInputNodeId(stepOptional.get().getWorkflowNode().getExecutionOrder().longValue());
                    action.setOriginalFileName(c.getOutputFileName());
                    action.setNewFileName(c.getInputFileName());
                    return action;
                })
                .filter(Objects::nonNull)
                .toList();

        dto.setInputs(actions);
    }

    // Abstract method to get the workflow orchestrator
    protected abstract WorkflowOrchestrator getWorkflowOrchestrator();

    protected abstract WorkflowDTO getWorkflowDTO(Long experimentId);

    protected abstract Scope getToolApiKeyScope();

    protected abstract String getWSBaseUrl();

    protected abstract void emitChange(Dto dto);

    protected abstract void startStep(StepEntity entity);

    protected abstract void saveFirstStepData(Entity entity, StepEntity stepEntity);
    //Optional methods to override

    public void handleStartFederatedLearningFC(StepEntity entity) {
        //IGNORE
    }

    public void uploadConfigToVolume(LinkedHashMap<String, Object> hyperParams, StartWorkflowNodeDTO
            startWorkflowDTO) {
        //IGNORE
    }

    public void linkData(StepEntity prevStep, StepEntity currentStep, List<WorkflowNodeInputActionDTO> inputs) {
        //IGNORE
    }

    public void onSaveStep(Entity experiment, StepDTO step) {
        //IGNORE
    }

    public void onUpdateStep(Entity experiment, StepDTO step) {
        //IGNORE
    }

    public void onErrorStep(StepDTO step) {
        //IGNORE
    }

    public void onPreFinishStep(StepDTO step) {
        //IGNORE
    }

    public void onFinishStep(Long experimentId, boolean experimentFinish) {
        //IGNORE
    }

    public BaseFileEntity getInputFile(Entity entity) {
        //IGNORE
        return null;
    }

    public Path getInputPath(Entity entity) {
        //IGNORE
        return null;
    }

    public void customizeStartWorkflowDto(StartWorkflowNodeDTO startWorkflow) {
        //IGNORE
    }

    protected WorkflowDTO getWorkflowDTO(Entity entity) {
        return getWorkflowDTO(entity.getId());
    }

    //overridable but with default
    protected List<ToolInputConfigDTO> getCurrentInputs(Entity entity) {
        WorkflowNodeDetailDTO currentNode = getCurrentNode(entity);
        if (currentNode == null) {
            return new ArrayList<>();
        }
        return currentNode.getAppInputConfig();
    }

    protected WorkflowNodeDetailDTO getCurrentNode(Entity entity) {
        WorkflowDTO workflow = getWorkflowDTO(entity);
        return workflow.getNodes()
                .stream()
                .filter(n -> n.getId()
                        .equals(entity.getCurrentWorkflowNode()
                                .getWorkflowNode()
                                .getId()))
                .findFirst()
                .orElse(null);
    }

}
