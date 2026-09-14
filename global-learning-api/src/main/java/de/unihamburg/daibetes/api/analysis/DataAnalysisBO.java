package de.unihamburg.daibetes.api.analysis;

import bio.cosy.feddb.core.api.model.prediction.DataAnalysisCreatePredictionDTO;
import bio.cosy.feddb.core.api.model.prediction.DataAnalysisPredictionDTO;
import bio.cosy.feddb.core.api.model.prediction.DataAnalysisStopPredictionDTO;
import bio.cosy.feddb.core.api.model.workflow.DataAnalysisDetailDTO;
import bio.cosy.feddb.core.api.model.workflow.ModelWorkflowDTO;
import bio.cosy.feddb.core.base.BaseBo;
import de.unihamburg.daibetes.api.analysis.prediction.DataAnalysisPredictionBO;
import de.unihamburg.daibetes.api.analysis.run.DataAnalysisRunBO;
import de.unihamburg.daibetes.api.analysis.worklfow.DataAnalysisWorkflowRunBO;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotAllowedException;

import java.util.List;
import java.util.Optional;


@ApplicationScoped
public class DataAnalysisBO extends BaseBo<ModelWorkflowDTO, DataAnalysisEntity, DataAnalysisAO, DataAnalysisMapper> {

    @Inject
    DataAnalysisRunBO modelRunBO;

    @Inject
    DataAnalysisWorkflowRunBO workflowRunBO;


    @Inject
    DataAnalysisPredictionBO modelPredictionBO;

    public List<ModelWorkflowDTO> list(String keycloakId) {
        return mapper.entitiesToDtos(ao.getAll(keycloakId));
    }

    public DataAnalysisDetailDTO findById(Long id, String keycloakId) {
        return ao.findById(keycloakId, id)
                .map(mapper::entityToDetail)
                .orElseThrow(() -> new NotAllowedException("Workflow not found"));
    }

    public DataAnalysisDetailDTO findByIdFlat(Long id, String keycloakId) {
        return ao.findById(keycloakId, id)
                .map(mapper::entityToDetailFlat)
                .orElseThrow(() -> new NotAllowedException("Workflow not found"));
    }

    public DataAnalysisEntity findEntityById(Long id, String keycloakId) {
        return ao.findById(keycloakId, id)
                .orElseThrow(() -> new NotAllowedException("Workflow not found"));
    }

    public void delete(Long id, String keycloakId) {
        ao.deleteById(keycloakId, id);
    }

    public DataAnalysisDetailDTO createWorkflow(String keycloakId, String name) {
        DataAnalysisEntity workflow = new DataAnalysisEntity();
        workflow.setKeycloakId(keycloakId);
        workflow.setName(name);
        ao.persist(workflow);
        return mapper.entityToDetail(workflow);
    }

    public Long createModelRun(Long id, DataAnalysisCreatePredictionDTO data, String keycloakId) {
        if (data.getWorkflowId() != null) {
            return workflowRunBO.startModel(data, keycloakId, id).getWorkflowId();
        } else {
            return modelRunBO.startModel(data, keycloakId, id).getId();
        }
    }

    @Transactional
    public void setLLMSummaryTransactional(Long id, String summary, String keycloakId) {
        Optional<DataAnalysisEntity> workflowOptional = ao.findById(keycloakId, id);
        if (workflowOptional.isEmpty()) {
            Log.errorf("Workflow with id %d not found for keycloakId %s", id, keycloakId);
            return;
        }
        DataAnalysisEntity workflow = workflowOptional.get();
        workflow.setLlmSummary(summary);
        ao.persist(workflow);
    }

    public void invalidateLlmSummary(Long id) {
        Optional<DataAnalysisEntity> workflowOptional = ao.findByIdOptional(id);
        if (workflowOptional.isEmpty()) {
            Log.errorf("Workflow with id %d not found", id);
            return;
        }
        DataAnalysisEntity workflow = workflowOptional.get();
        if (workflow.getLlmSummary() == null) {
            return;
        }
        workflow.setLlmSummary(null);
        ao.persist(workflow);
    }

    @Transactional
    public Optional<String> getLLSummaryOptionalTransactional(Long id, String keycloakId) {
        return ao.findById(keycloakId, id)
                .map(DataAnalysisEntity::getLlmSummary);

    }

    public DataAnalysisPredictionDTO stopRun(Long dataAnalysisId, Long id, DataAnalysisStopPredictionDTO dto, String keycloakId) {
        ao.findById(keycloakId, dataAnalysisId)
                .orElseThrow(() -> new NotAllowedException("Workflow not found"));
        Log.info("Keycloak ID: " + keycloakId);
        if (dto != null && dto.getWorkflowId() != null) {
            workflowRunBO.stopRun(dto.getWorkflowId());
            return null;
        } else {
            return modelPredictionBO.stopRunById(id, keycloakId);
        }
    }

    public void deleteRun(Long dataAnalysisId, Long id, Long workflowId, String keycloakId) {
        ao.findById(keycloakId, dataAnalysisId)
                .orElseThrow(() -> new NotAllowedException("Workflow not found"));
        Log.info("Keycloak ID: " + keycloakId);
        if (workflowId != null) {
            workflowRunBO.delete(workflowId);
        } else {
            modelPredictionBO.delete(id, keycloakId);
        }
    }
}
