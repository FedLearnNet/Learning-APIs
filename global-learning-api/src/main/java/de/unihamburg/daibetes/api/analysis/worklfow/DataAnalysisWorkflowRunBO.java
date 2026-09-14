package de.unihamburg.daibetes.api.analysis.worklfow;

import bio.cosy.feddb.core.api.model.prediction.DataAnalysisCreatePredictionDTO;
import bio.cosy.feddb.core.api.model.prediction.DataAnalysisOrchestrator;
import bio.cosy.feddb.core.api.model.prediction.DataAnalysisRunModesEnum;
import bio.cosy.feddb.core.api.project.ProjectStatus;
import bio.cosy.feddb.core.api.run.FinishRunDTO;
import bio.cosy.feddb.core.api.run.RunStatusTypes;
import bio.cosy.feddb.core.api.run.StartRunDTO;
import bio.cosy.feddb.core.api.run.UpdateRunDTO;
import de.unihamburg.daibetes.api.analysis.DataAnalysisResultDTO;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import bio.cosy.feddb.core.api.workflow.WorkflowDTO;
import bio.cosy.feddb.core.api.workflow.WorkflowInputDTO;
import bio.cosy.feddb.core.api.workflow.base.BaseWorkflowEngine;
import bio.cosy.feddb.core.api.workflow.base.experiment.BaseWorkflowExperimentBO;
import bio.cosy.feddb.core.api.workflow.connection.WorkflowConnectionDTO;
import bio.cosy.feddb.core.api.workflow.node.WorkflowNodeDetailDTO;
import bio.cosy.feddb.core.base.BaseFileEntity;
import bio.cosy.feddb.core.services.orch.WorkflowOrchestrator;
import bio.cosy.feddb.core.services.orch.dto.StartWorkflowNodeDTO;
import bio.cosy.feddb.core.services.orch.dto.WorkflowNodeInputActionDTO;
import bio.cosy.feddb.core.security.ToolApiKeyService;
import bio.cosy.feddb.core.security.Scope;
import de.unihamburg.daibetes.api.analysis.DataAnalysisBO;
import de.unihamburg.daibetes.api.analysis.DataAnalysisEntity;
import de.unihamburg.daibetes.api.analysis.DataAnalysisResultSender;
import de.unihamburg.daibetes.api.analysis.file.DataAnalysisFileBO;
import de.unihamburg.daibetes.api.analysis.file.DataAnalysisFileEntity;
import de.unihamburg.daibetes.api.analysis.worklfow.step.DataAnalysisWorkflowRunStepBO;
import de.unihamburg.daibetes.api.analysis.worklfow.step.DataAnalysisWorkflowRunStepDTO;
import de.unihamburg.daibetes.api.analysis.worklfow.step.DataAnalysisWorkflowRunStepEntity;
import de.unihamburg.daibetes.api.file.FileBO;
import de.unihamburg.daibetes.api.file.FileEntity;
import de.unihamburg.daibetes.api.workflow.WorkflowAO;
import de.unihamburg.daibetes.api.workflow.WorkflowBO;
import de.unihamburg.daibetes.api.workflow.WorkflowEntity;
import de.unihamburg.daibetes.services.WorkflowOrchestratorBO;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.LockModeType;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;


@ApplicationScoped
public class DataAnalysisWorkflowRunBO
        extends BaseWorkflowExperimentBO<DataAnalysisWorkflowRunDTO,
        DataAnalysisWorkflowRunStepEntity,
        DataAnalysisWorkflowRunStepDTO,
        DataAnalysisWorkflowRunEntity,
        DataAnalysisWorkflowRunAO,
        DataAnalysisWorkflowRunMapper,
        DataAnalysisWorkflowRunStepBO> {

    @Inject
    WorkflowOrchestratorBO workflowOrchestrator;
    @Inject
    DataAnalysisResultSender resultSender;

    // Single source of truth for the overhead flag; runtime is always exposed regardless.
    @Inject
    @ConfigProperty(name = "posymed.runtime.overhead.enabled", defaultValue = "false")
    boolean overheadEnabled;

    @Inject
    WorkflowBO workflowBO;

    @Inject
    WorkflowAO workflowAO;

    @Inject
    DataAnalysisFileBO modelWorkflowFileBO;

    @Inject
    FileBO fileBO;

    @Inject
    DataAnalysisBO dataAnalysisBO;

    @Override
    protected WorkflowOrchestrator getWorkflowOrchestrator() {
        return workflowOrchestrator;
    }

    @Override
    protected WorkflowDTO getWorkflowDTO(Long experimentId) {
        return getWorkflowDTO(ao.findById(experimentId));
    }

    @Override
    protected WorkflowDTO getWorkflowDTO(DataAnalysisWorkflowRunEntity runEntity) {
        return workflowBO.entityToDto(runEntity.getWorkflow());
    }

    @Override
    protected Scope getToolApiKeyScope() {
        return Scope.MODEL_WORKFLOW_RUN;
    }

    @Override
    protected String getWSBaseUrl() {
        return DataAnalysisOrchestrator.getUrlForRunning(DataAnalysisRunModesEnum.WORKFLOW);
    }

    @Override
    protected void emitChange(DataAnalysisWorkflowRunDTO dto) {
        try {
            if (dto.getCurrentWorkflowNodeId() != null) {
                resultSender.sendMessage(stepBo.getResult(dto.getCurrentWorkflowNodeId()));
            } else {
                Log.warnf("Workflow run with id %d has no current workflow node id, cant send workflow run data", dto.getId());
            }
            if (dto.getDataAnalysisId() != null) {
                dataAnalysisBO.invalidateLlmSummary(dto.getDataAnalysisId());
            } else {
                Log.warnf("Workflow run with id %d has no data analysis id, cant invalidate llm summary cache", dto.getId());
            }
        } catch (NotFoundException e) {
            Log.warnf("Error emitting workflow run update for workflow run id %d: %s", dto.getId(), e.getMessage());
            //ignore
        }
    }

    @Override
    protected void startStep(DataAnalysisWorkflowRunStepEntity entity) {
        //is done in WS
    }

    @Override
    protected void saveFirstStepData(DataAnalysisWorkflowRunEntity entity, DataAnalysisWorkflowRunStepEntity stepEntity) {
        uploadData(stepEntity);
    }

    public DataAnalysisWorkflowRunDTO findById(Long id, Long dataAnalysisId, String keycloakId) {
        return ao.findById(id, dataAnalysisId, keycloakId)
                .map(mapper::entityToDto)
                .orElseThrow(() -> new NotFoundException("Data analysis workflow run with id " + id + " not found"));
    }

    @Transactional
    public DataAnalysisWorkflowRunDTO createRunTransactional(DataAnalysisCreatePredictionDTO createModel, String keycloakId, Long dataAnalysisId) throws IllegalArgumentException {
        DataAnalysisEntity dataAnalysis = dataAnalysisBO.findEntityById(dataAnalysisId, keycloakId);

        WorkflowDTO workflowDTO = workflowBO.get(createModel.getWorkflowId(), keycloakId);
        if (workflowDTO == null) {
            throw new NotFoundException("Workflow not found");
        }

        WorkflowNodeDetailDTO firstNode = baseWorkflowEngine.firstNode(workflowDTO);
        if (firstNode == null) {
            throw new NotFoundException("Workflow has no starting node");
        }
        DataAnalysisWorkflowRunDTO prediction = create(workflowDTO.getId(), firstNode.getId(), dataAnalysis, keycloakId, createModel.getInputs());

        ao.flush();
        return prediction;
    }

    public DataAnalysisWorkflowRunDTO startModel(DataAnalysisCreatePredictionDTO createModel, String keycloakId, Long dataAnalysisId) {
        DataAnalysisWorkflowRunDTO prediction = createRunTransactional(createModel, keycloakId, dataAnalysisId);
        handleInitLearning(prediction.getId());
        return prediction;
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public DataAnalysisWorkflowRunDTO create(Long workflowId, Long firstNodeId, DataAnalysisEntity dataAnalysis, String keycloakId, LinkedHashMap<String, Object> inputs) {
        DataAnalysisWorkflowRunEntity entity = new DataAnalysisWorkflowRunEntity();
        entity.setWorkflow(workflowAO.findById(workflowId));
        entity.setKeycloakId(keycloakId);
        entity.setDataAnalysis(dataAnalysis);
        ao.persist(entity);
        List<DataAnalysisWorkflowRunStepEntity> steps = stepBo.createForWorkflow(entity);
        saveData(entity.getWorkflow(), keycloakId, steps, inputs);
        DataAnalysisWorkflowRunDTO created = mapper.entityToDto(entity);
        resultSender.sendMessage(created);
        return created;
    }

    private void uploadData(DataAnalysisWorkflowRunStepEntity currentNode) {
        try {
            Log.info("Starting data import for experiment workflow run step ID: " + currentNode.getId());
            Map<String, File> data = currentNode.getFiles().stream()
                    .collect(Collectors.toMap(
                            DataAnalysisFileEntity::getInputName,
                            input -> fileBO.loadFile(input.getFile())
                    ));
            if (data.isEmpty()) {
                Log.warn("No data found to upload for learning.");
                return;
            }
            Log.info("Uploading data for learning: " + data.size() + " files found.");
            for (Map.Entry<String, File> entry : data.entrySet()) {
                Log.info("Uploading file: " + entry.getKey() + " to analysis experiment ID: " + currentNode.getExperiment().getId() + ", run: " + currentNode.getId());
                workflowOrchestrator.uploadFilesToVolume(currentNode.getExperiment().getId(), currentNode.getWorkflowNode().getExecutionOrder().longValue(), entry.getValue(), entry.getKey());
            }
        } catch (Exception e) {
            Log.error("Error during data export for data analysis experiment ID: " + currentNode.getExperiment().getId() + ", run: " + currentNode.getId(), e);
        }
    }

    public void saveData(WorkflowEntity workflowEntity, String keycloakId, List<DataAnalysisWorkflowRunStepEntity> steps, LinkedHashMap<String, Object> inputs) {
        WorkflowDTO workflow = workflowBO.entityToDto(workflowEntity);
        BaseWorkflowEngine baseWorkflowEngine = new BaseWorkflowEngine();

        Map<WorkflowInputDTO, Map<WorkflowConnectionDTO, WorkflowNodeDetailDTO>> inputNodeMap = baseWorkflowEngine.getInputNodes(workflow);
        for (Map.Entry<WorkflowInputDTO, Map<WorkflowConnectionDTO, WorkflowNodeDetailDTO>> entry : inputNodeMap.entrySet()) {
            Object input = inputs.get(entry.getKey().getName());
            if (input == null) {
                continue;
            }
            for (Map.Entry<WorkflowConnectionDTO, WorkflowNodeDetailDTO> connection : entry.getValue().entrySet()) {
                String output = connection.getKey().getInputFileName();
                DataAnalysisWorkflowRunStepEntity step = steps.stream()
                        .filter(s -> s.getWorkflowNode().getId().equals(connection.getValue().getId()))
                        .findFirst()
                        .orElseThrow(() -> new NotFoundException("Workflow step not found for node ID: " + connection.getValue().getId()));
                modelWorkflowFileBO.createInputCopy(output, input, keycloakId, step);
            }
        }
    }

    @Override
    public BaseFileEntity getInputFile(DataAnalysisWorkflowRunEntity step) {
        return new FileEntity(); // just not to be null
    }

    @Override
    public void onFinishStep(Long experimentId, boolean experimentFinish) {
        if (!experimentFinish) {
            handleInitLearning(experimentId);
        }
    }

    @Override
    public void linkData(DataAnalysisWorkflowRunStepEntity prevStep, DataAnalysisWorkflowRunStepEntity currentStep, List<WorkflowNodeInputActionDTO> inputs) {
        Set<DataAnalysisFileEntity> outputFiles = prevStep.getOutputFiles();
        for (WorkflowNodeInputActionDTO action : inputs) {

            outputFiles.stream()
                    .filter(f -> {
                        String originalFileName = action.getOriginalFileName();
                        int lastDot = originalFileName.lastIndexOf('.');
                        String origInputName = lastDot > 0
                                ? originalFileName.substring(0, lastDot)
                                : originalFileName;
                        return f.getOutputName().equals(origInputName);
                    })
                    .findFirst()
                    .ifPresent(f -> stepBo.linkData(action.getNewFileName(), f, currentStep));
        }
    }

    @Override
    public void onPreFinishStep(DataAnalysisWorkflowRunStepDTO step) {
        //IGNORE as we set startWorkflow.setEnableRemoteResultSaving(true);
    }

    public void updateAndNotify(Long id, UpdateRunDTO updateTest) {

        DataAnalysisWorkflowRunStepDTO updated = updateStatus(id, updateTest);
        if (updateTest.getStatus().equals(RunStatusTypes.ERROR)) {
            workflowOrchestrator.cleanup(updated.getExperimentId());
        }
        resultSender.sendMessage(stepBo.getResult(updated.getId()));
    }

    public void updateAndNotify(Long id, FinishRunDTO updateTest) {

        DataAnalysisWorkflowRunStepDTO updated = updateStatus(id, updateTest);
        DataAnalysisResultDTO result = stepBo.getResult(updated.getId());
        // Strip the overhead breakdown from the notified result unless the flag is enabled.
        if (result.getMeta() != null) {
            result.getMeta().withOverheadVisibility(overheadEnabled);
        }
        resultSender.sendMessage(result);
    }

    public void updateAndNotify(Long id, StartRunDTO updateTest) {

        DataAnalysisWorkflowRunStepDTO updated = updateStatus(id, updateTest);
        resultSender.sendMessage(stepBo.getResult(updated.getId()));
    }

    public void stopRunByStep(Long id) {
        DataAnalysisWorkflowRunStepDTO step = stepBo.getById(id);
        if (step == null) {
            Log.errorf("Workflow run step with id %d not found", id);
            return;
        }
        if (RunStatusTypes.isFinalStatus(step.getStepStatus())) {
            return;
        }
        stopRun(step.getExperimentId());
    }

    public DataAnalysisWorkflowRunDTO stopRun(Long workflowId) {
        DataAnalysisWorkflowRunEntity runEntity = ao.findByIdOptional(workflowId, LockModeType.PESSIMISTIC_WRITE)
                .orElseThrow(() -> new NotFoundException("Workflow run not found"));

        if (!ProjectStatus.isFinalStatus(runEntity.getExperimentStatus())) {
            cleanup(workflowId);
            runEntity.setExperimentStatus(ProjectStatus.STOPPED);
            DataAnalysisWorkflowRunDTO dto = mapper.entityToDto(runEntity);
            resultSender.sendMessage(dto);
            return dto;
        }
        return entityToDto(runEntity);
    }

    public void delete(Long workflowId) {
        DataAnalysisWorkflowRunEntity runEntity = ao.findByIdOptional(workflowId)
                .orElseThrow(() -> new NotFoundException("Workflow run not found"));

        if (!ProjectStatus.isFinalStatus(runEntity.getExperimentStatus())) {
            cleanup(workflowId);
        }
        ao.delete(runEntity);
    }

    public void customizeStartWorkflowDto(StartWorkflowNodeDTO startWorkflow) {
        startWorkflow.setEnableRemoteResultSaving(true);
    }
}
