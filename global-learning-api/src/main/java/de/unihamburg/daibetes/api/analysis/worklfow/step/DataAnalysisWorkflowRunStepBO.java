package de.unihamburg.daibetes.api.analysis.worklfow.step;

import bio.cosy.feddb.core.api.run.AppRunUploadData;
import bio.cosy.feddb.core.api.run.RunMetaDTO;
import bio.cosy.feddb.core.api.run.RunStatusTypes;
import bio.cosy.feddb.core.api.workflow.base.step.BaseWorkflowStepBO;
import de.unihamburg.daibetes.api.analysis.DataAnalysisResultDTO;
import de.unihamburg.daibetes.api.analysis.DataAnalysisResultSender;
import de.unihamburg.daibetes.api.analysis.file.DataAnalysisFileBO;
import de.unihamburg.daibetes.api.analysis.file.DataAnalysisFileEntity;
import de.unihamburg.daibetes.api.analysis.worklfow.DataAnalysisWorkflowRunEntity;
import de.unihamburg.daibetes.api.runs.base.HyperParamMapper;
import de.unihamburg.daibetes.api.workflow.node.WorkflowNodeEntity;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;


@ApplicationScoped
public class DataAnalysisWorkflowRunStepBO extends BaseWorkflowStepBO<DataAnalysisWorkflowRunStepDTO, DataAnalysisWorkflowRunStepEntity, DataAnalysisWorkflowRunStepAO, DataAnalysisWorkflowRunStepMapper> {

    @Inject
    DataAnalysisFileBO modelWorkflowFileBO;

    @Inject
    DataAnalysisResultSender resultSender;

    @Inject
    HyperParamMapper hyperParamMapper;

    // Single source of truth for the overhead flag; runtime is always exposed regardless.
    @Inject
    @ConfigProperty(name = "posymed.runtime.overhead.enabled", defaultValue = "false")
    boolean overheadEnabled;

    public DataAnalysisResultDTO getResult(Long stepId) {
        DataAnalysisWorkflowRunStepEntity stepEntity = ao.findById(stepId);
        if (stepEntity == null) {
            throw new NotFoundException("Step with ID " + stepId + " not found.");
        }
        return mapper.entityToResultDTO(stepEntity);
    }

    public List<DataAnalysisWorkflowRunStepEntity> createForWorkflow(DataAnalysisWorkflowRunEntity entity) {
        List<DataAnalysisWorkflowRunStepEntity> steps = new ArrayList<>();
        for (WorkflowNodeEntity node : entity.getWorkflow().getNodes()) {
            DataAnalysisWorkflowRunStepEntity stepEntity = new DataAnalysisWorkflowRunStepEntity();
            stepEntity.setStepStatus(RunStatusTypes.INITIALIZED);
            stepEntity.setWorkflowNode(node);
            stepEntity.setExperiment(entity);
            ao.persist(stepEntity);
            steps.add(stepEntity);
        }
        return steps;
    }

    public DataAnalysisWorkflowRunStepEntity finishEntityById(Long id, LinkedHashMap<String, Object> fields,
                                                              RunMetaDTO meta) {
        String jsonString = hyperParamMapper.hyperparamsToJsonStringAllowError(fields);
        ao.updateFinishedTransactional(id, jsonString);
        DataAnalysisWorkflowRunStepEntity entity = ao.findById(id);
        if (meta != null) {
            // The upload is the terminal call for workflow runs, so persist the wrapper-measured
            // timing here (the websocket FINISH_RUN never arrives — the container is torn down).
            entity.setMeta(meta);
            ao.persist(entity);
        }
        return entity;
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void uploadOutputTransactional(Long runId, AppRunUploadData req, String keycloakId) {
        Log.info("Starting cleanup for runId: " + runId);
        DataAnalysisWorkflowRunStepEntity prediction = finishEntityById(runId, req.getFieldsNullsafe(), req.getMeta());
        modelWorkflowFileBO.storePredictionFileUpload(prediction, req.getFiles());
        DataAnalysisResultDTO result = mapper.entityToResultDTO(prediction);
        if (result.getMeta() != null) {
            result.getMeta().withOverheadVisibility(overheadEnabled);
        }
        resultSender.sendMessage(result);
    }


    public void uploadOutput(Long runId, AppRunUploadData req, String keycloakId) {
        uploadOutputTransactional(runId, req, keycloakId);
        //TODO workflowOrchestrator.cleanupWorkflowNode(runId);
    }

    public byte[] downloadOutput(String keycloakId, String containerId) {
        DataAnalysisWorkflowRunStepEntity prediction = ao.getByContainerId(containerId, keycloakId)
                .orElseThrow(() -> new NotFoundException("Data analysis prediction not found for container ID: " + containerId));
        Set<DataAnalysisFileEntity> files = prediction.getFiles();
        return modelWorkflowFileBO.downloadOutput(files);
    }

    public void linkData(String variableName, DataAnalysisFileEntity file, DataAnalysisWorkflowRunStepEntity currentStep) {
        modelWorkflowFileBO.createInputCopy(variableName, file, currentStep);
    }
}
