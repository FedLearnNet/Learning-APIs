package de.unihamburg.daibetes.api.analysis.prediction;

import bio.cosy.feddb.core.api.model.prediction.DataAnalysisCreatePredictionDTO;
import bio.cosy.feddb.core.api.model.prediction.DataAnalysisPredictionDTO;
import bio.cosy.feddb.core.api.run.FinishRunDTO;
import bio.cosy.feddb.core.api.run.RunMetaDTO;
import bio.cosy.feddb.core.api.run.RunStatusTypes;
import bio.cosy.feddb.core.base.BaseBo;
import de.unihamburg.daibetes.api.analysis.DataAnalysisBO;
import de.unihamburg.daibetes.api.analysis.DataAnalysisEntity;
import de.unihamburg.daibetes.api.analysis.DataAnalysisResultSender;
import de.unihamburg.daibetes.api.analysis.file.DataAnalysisFileBO;
import de.unihamburg.daibetes.api.analysis.file.DataAnalysisFileEntity;
import de.unihamburg.daibetes.api.analysis.run.DataAnalysisRunBO;
import de.unihamburg.daibetes.api.app.author.FederatedAppAuthorBO;
import de.unihamburg.daibetes.api.app.version.FederatedAppVersionAO;
import de.unihamburg.daibetes.api.file.FileBO;
import de.unihamburg.daibetes.api.model.access.ModelAccessBO;
import de.unihamburg.daibetes.api.model.sub.ModelSubAO;
import de.unihamburg.daibetes.api.runs.base.HyperParamMapper;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.LockModeType;
import jakarta.ws.rs.NotAllowedException;
import jakarta.ws.rs.NotFoundException;
import org.apache.commons.lang3.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;


@ApplicationScoped
public class DataAnalysisPredictionBO extends BaseBo<DataAnalysisPredictionDTO, DataAnalysisPredictionEntity, DataAnalysisPredictionAO, DataAnalysisPredictionMapper> {

    @Inject
    ModelAccessBO accessBO;

    @Inject
    FederatedAppAuthorBO authorBO;

    @Inject
    FederatedAppVersionAO appVersionAO;

    @Inject
    ModelSubAO modelSubAO;

    @Inject
    DataAnalysisResultSender resultSender;

    @Inject
    DataAnalysisFileBO modelWorkflowFileBO;

    @Inject
    HyperParamMapper hyperParamMapper;

    @Inject
    DataAnalysisBO dataAnalysisBO;

    @Inject
    DataAnalysisRunBO modelRunBO;
    @Inject
    FileBO fileBO;

    public DataAnalysisPredictionEntity finishEntityById(Long id, LinkedHashMap<String, Object> fields,
                                                         RunMetaDTO meta) {
        DataAnalysisPredictionEntity prediction = ao.findById(id, LockModeType.PESSIMISTIC_WRITE);
        prediction.setStatus(RunStatusTypes.FINISHED);
        String jsonString = hyperParamMapper.hyperparamsToJsonStringAllowError(fields);
        prediction.setResult(jsonString);
        if (meta != null) {
            // The upload is the terminal call for data-analysis runs, so persist the wrapper-measured
            // timing here (the websocket FINISH_RUN never arrives — the container is torn down).
            prediction.setMeta(meta);
        }
        ao.persist(prediction);
        return prediction;
    }

    /**
     * Persists the wrapper-measured lifecycle timing onto the prediction. Called within the finish
     * transaction before the status update so the notified result message already carries runtime.
     * No-op when the run reported no timing (e.g. historical/ERROR runs).
     */
    public void persistTiming(Long id, FinishRunDTO finishTest) {
        if (finishTest == null) {
            return;
        }
        DataAnalysisPredictionEntity prediction = ao.findById(id, LockModeType.PESSIMISTIC_WRITE);
        if (prediction == null) {
            return;
        }
        prediction.setMeta(finishTest.getMeta());
        ao.persist(prediction);
    }

    public List<DataAnalysisPredictionDTO> getAll(String keycloakId) {
        return mapper.entitiesToDtos(ao.getAll(keycloakId));
    }

    public DataAnalysisPredictionDTO getById(Long id, String keycloakId) {
        return ao.getById(id, keycloakId).map(mapper::entityToDto).orElseThrow(() -> new NotFoundException("Data analysis prediction not found"));
    }

    public DataAnalysisPredictionDTO stopRunById(Long id, String keycloakId) {
        Optional<DataAnalysisPredictionEntity> entityOpt = ao.getById(id, keycloakId);
        if (entityOpt.isPresent()) {
            DataAnalysisPredictionEntity entity = entityOpt.get();
            if (!RunStatusTypes.isFinalStatus(entity.getStatus())) {
                try {
                    modelRunBO.cleanup(entity.getContainerId());
                } catch (Exception e) {
                    Log.errorf("Error during cleanup of container %s for prediction %d: %s", entity.getContainerId(), id, e.getMessage(), e);
                }
                entity.setStatus(RunStatusTypes.STOPPED);
                ao.persist(entity);
                DataAnalysisPredictionDTO dto = mapper.entityToDto(entity);
                resultSender.sendMessage(dto);
                return dto;
            } else {
                throw new NotAllowedException("Only running predictions can be stopped");
            }
        } else {
            throw new NotFoundException("Data analysis prediction not found");
        }
    }

    public void delete(Long id, String keycloakId) {
        Optional<DataAnalysisPredictionEntity> entityOpt = ao.getById(id, keycloakId);
        if (entityOpt.isPresent()) {
            DataAnalysisPredictionEntity entity = entityOpt.get();
            if (!RunStatusTypes.isFinalStatus(entity.getStatus())) {
                try {
                    modelRunBO.cleanup(entity.getContainerId());
                } catch (Exception e) {
                    Log.errorf("Error during cleanup of container %s for prediction %d: %s", entity.getContainerId(), id, e.getMessage(), e);
                }
            }
            ao.delete(entity);
        }
    }

    public List<DataAnalysisPredictionDTO> getAllForModel(Long modelId, String keycloakId) {
        return mapper.entitiesToDtos(ao.getAllForModel(keycloakId, modelId));
    }

    public List<DataAnalysisPredictionDTO> getAllPending() {
        return mapper.entitiesToDtos(ao.getAllPending());
    }

    public DataAnalysisPredictionDTO createForApp(Long appVersionId, DataAnalysisCreatePredictionDTO dto, String keycloakId) {
        if (authorBO.isUserAuthorVersionId(keycloakId, appVersionId)) {
            DataAnalysisPredictionEntity entity = mapper.createDtoToEntity(dto);
            entity.setFederatedAppVersion(appVersionAO.findById(appVersionId));
            return createViaEntity(entity, keycloakId, dto.getInputs());
        }
        throw new NotAllowedException("User has no rights to create a model prediction");
    }

    public DataAnalysisPredictionDTO createForApp(DataAnalysisEntity workflow, Long appVersionId, DataAnalysisCreatePredictionDTO dto, String keycloakId) {
        if (authorBO.isUserAuthorVersionId(keycloakId, appVersionId)) {
            DataAnalysisPredictionEntity entity = mapper.createDtoToEntityWorkflow(dto, workflow.getId(), keycloakId);
            entity.setFederatedAppVersion(appVersionAO.findById(appVersionId));
            return createViaEntity(entity, keycloakId, dto.getInputs());

        }
        throw new NotAllowedException("User has no rights to create a model prediction");
    }

    public DataAnalysisPredictionDTO create(Long modelId, DataAnalysisCreatePredictionDTO dto, String keycloakId) {
        if (accessBO.hasUserAnyRights(keycloakId, modelId)) {
            if (modelSubAO.isModelIdCorrespondingToModelSubId(modelId, dto.getModelSubId())) {
                DataAnalysisPredictionEntity entity = mapper.createDtoToEntity(dto);
                entity.setSubModel(modelSubAO.findById(dto.getModelSubId()));
                return createViaEntity(entity, keycloakId, dto.getInputs());
            }
        }
        throw new NotAllowedException("User has no rights to create a model prediction");
    }

    public DataAnalysisPredictionDTO create(DataAnalysisEntity workflow, Long modelId, DataAnalysisCreatePredictionDTO dto, String keycloakId) {
        if (accessBO.hasUserAnyRights(keycloakId, modelId)) {
            if (modelSubAO.isModelIdCorrespondingToModelSubId(modelId, dto.getModelSubId())) {
                DataAnalysisPredictionEntity entity = mapper.createDtoToEntityWorkflow(dto, workflow.getId(), keycloakId);
                entity.setSubModel(modelSubAO.findById(dto.getModelSubId()));
                return createViaEntity(entity, keycloakId, dto.getInputs());
            }
        }
        throw new NotAllowedException("User has no rights to create a model prediction");
    }

    private DataAnalysisPredictionDTO createViaEntity(DataAnalysisPredictionEntity entity, String keycloakId, LinkedHashMap<String, Object> inputs) {
        entity.setKeycloakId(keycloakId);
        ao.persist(entity);
        modelWorkflowFileBO.createInputCopy(inputs, keycloakId, entity);
        DataAnalysisPredictionDTO created = mapper.entityToDto(entity);
        resultSender.sendMessage(created);
        dataAnalysisBO.invalidateLlmSummary(created.getDataAnalysisId());
        return created;
    }

    @Override
    public DataAnalysisPredictionDTO update(DataAnalysisPredictionDTO dto) {
        dto = super.update(dto);
        resultSender.sendMessage(dto);
        dataAnalysisBO.invalidateLlmSummary(dto.getDataAnalysisId());
        return dto;
    }

    public void updateAndNotify(Long id, RunStatusTypes status, String logMessage) {
        DataAnalysisPredictionEntity prediction = ao.findById(id, LockModeType.PESSIMISTIC_WRITE);

        if (!RunStatusTypes.isFinalStatus(prediction.getStatus())) {
            if (status != null) {
                prediction.setStatus(status);
            }

            if (prediction.getStatus().equals(RunStatusTypes.ERROR)) {
                prediction.setLastError(logMessage);
            } else {
                if (StringUtils.isNotEmpty(logMessage)) {
                    prediction.setLastLog(logMessage);
                }
            }
            ao.persist(prediction);
            dataAnalysisBO.invalidateLlmSummary(prediction.getDataAnalysis().getId());
        }
        DataAnalysisPredictionDTO predictionDTO = mapper.entityToDto(prediction);
        resultSender.sendMessage(predictionDTO);


        if (prediction.getStatus().equals(RunStatusTypes.ERROR)) {
            Log.errorf("Run %d finished with error, will cleanup: %s", id, prediction.getLastError());
            if (StringUtils.isEmpty(prediction.getContainerId())) {
                Log.errorf("No containerId found for prediction %d, skipping cleanup", id);
            }
            modelRunBO.cleanup(prediction.getContainerId());
        }
    }

    public void updateConsoleMsg(Long id, String logMessage) {
        Log.debugf("Updating console message for prediction %d: %s", id, logMessage);
        try {
            DataAnalysisPredictionEntity prediction = ao.findById(id, LockModeType.PESSIMISTIC_WRITE);
            if (prediction == null) {
                return;
            }
            if (StringUtils.isEmpty(prediction.getRawLog())) {
                prediction.setRawLog(logMessage);
            } else {
                prediction.setRawLog(prediction.getRawLog() + "\n" + logMessage);
            }
            ao.persist(prediction);
            DataAnalysisPredictionDTO predictionDTO = mapper.entityToDto(prediction);
            resultSender.sendMessage(predictionDTO);
        } catch (Exception e) {
            Log.errorf("Error updating console message for prediction %d: %s", id, e.getMessage(), e);
        }
    }

    @Override
    public DataAnalysisPredictionEntity mergeEntity(DataAnalysisPredictionEntity foundEntity, DataAnalysisPredictionEntity entity) {
        entity.setDataAnalysis(foundEntity.getDataAnalysis());
        return entity;
    }


    public byte[] downloadOutput(String keycloakId, String containerId) {
        DataAnalysisPredictionEntity prediction = ao.getByContainerId(containerId, keycloakId)
                .orElseThrow(() -> new NotFoundException("Data analysis prediction not found for container ID: " + containerId));
        Set<DataAnalysisFileEntity> files = prediction.getFiles();
        return modelWorkflowFileBO.downloadOutput(files);
    }
}
