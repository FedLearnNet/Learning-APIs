package de.unihamburg.daibetes.api.model.sub;

import bio.cosy.feddb.core.api.model.FederatedModelSubDetailDataDTO;
import bio.cosy.feddb.core.api.model.ModelSubDTO;
import bio.cosy.feddb.core.api.model.ModelSubStatus;
import bio.cosy.feddb.core.api.project.ProjectDTO;
import bio.cosy.feddb.core.api.project.ProjectDetailDTO;
import bio.cosy.feddb.core.api.query.QueryDTO;
import bio.cosy.feddb.core.base.BaseBo;
import de.unihamburg.daibetes.api.analysis.prediction.DataAnalysisPredictionAO;
import de.unihamburg.daibetes.api.model.access.ModelAccessBO;
import de.unihamburg.daibetes.api.model.sub.file.ModelSubFileBO;
import de.unihamburg.daibetes.api.project.ProjectMapper;
import de.unihamburg.daibetes.api.project.experiment.federated.ProjectFederatedExperimentEntity;
import de.unihamburg.daibetes.api.project.experiment.federated.ProjectFederatedExperimentMapper;
import de.unihamburg.daibetes.api.query.QueryAO;
import de.unihamburg.daibetes.api.query.QueryEntity;
import de.unihamburg.daibetes.api.query.QueryMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotFoundException;

import java.io.File;
import java.util.Collections;
import java.util.Optional;


@ApplicationScoped
public class ModelSubBO extends BaseBo<ModelSubDTO, ModelSubEntity, ModelSubAO, ModelSubMapper> {

    @Inject
    ModelAccessBO accessBO;

    @Inject
    DataAnalysisPredictionAO modelPredictionAO;

    @Inject
    ModelSubFileBO modelSubFileBO;

    @Inject
    ProjectFederatedExperimentMapper federatedExperimentMapper;

    @Inject
    ProjectMapper projectMapper;

    @Inject
    QueryAO queryAO;

    @Inject
    QueryMapper queryMapper;

    public ModelSubDTO entityToStoreDto(ModelSubEntity entity) {
        ModelSubDTO dto = mapper.entityToDto(entity);
        dto.setFederatedData(createFederatedData(entity));
        return dto;
    }

    public ModelSubDTO getStoreById(Long id) {
        ModelSubEntity entity = ao.findByIdOptional(id)
                .orElseThrow(() -> new NotFoundException(String.format("Model variant with the ID %s not found", id)));
        return entityToStoreDto(entity);
    }

    private Optional<FederatedModelSubDetailDataDTO> createFederatedData(ModelSubEntity modelSub) {
        ProjectFederatedExperimentEntity experiment = modelSub.getFederatedExperiment();
        if (experiment == null) {
            return Optional.empty();
        }

        ProjectDTO project = getProjectSnapshot(experiment);
        FederatedModelSubDetailDataDTO federatedData = new FederatedModelSubDetailDataDTO();
        federatedData.setProject(project);
        federatedData.setQuery(getRedactedQuerySnapshot(project, experiment).orElse(null));
        return Optional.of(federatedData);
    }

    private ProjectDTO getProjectSnapshot(ProjectFederatedExperimentEntity experiment) {
        ProjectDetailDTO snapshot = federatedExperimentMapper.jsonStringToProject(experiment.getProjectVersion());
        ProjectDTO project = snapshot != null ? snapshot : projectMapper.entityToSimpleDTO(experiment.getProject());

        // Uploaded source files are not needed for traceability and may contain private metadata.
        project.setFile(null);
        return project;
    }

    private Optional<QueryDTO> getRedactedQuerySnapshot(ProjectDTO project,
                                                         ProjectFederatedExperimentEntity experiment) {
        QueryEntity query = null;
        if (project.getQueryId() != null) {
            query = queryAO.findByIdOptional(project.getQueryId()).orElse(null);
        }
        if (query == null && experiment.getProject() != null) {
            query = experiment.getProject().getQuery();
        }
        if (query == null) {
            return Optional.empty();
        }

        QueryDTO dto = queryMapper.entityToDto(query);
        // Counts/results and ownership/membership information stay private until a disclosure policy is agreed.
        dto.setResult(null);
        dto.setError(null);
        dto.setKeycloakId(null);
        dto.setProjectIds(Collections.emptySet());
        dto.setRoles(Collections.emptySet());
        dto.setLatestDataStatisticsRequest(null);
        dto.setLatestDataStatisticsRequestTimestamp(null);
        return Optional.of(dto);
    }

    public ModelSubDTO createInitial(Long modelVersionId, bio.cosy.feddb.core.api.model.ModelSubDataDTO dto) {
        ModelSubDTO newSubDto = new ModelSubDTO();
        newSubDto.setExperimentRunId(dto.getRunId());
        newSubDto.setModelPath(dto.getFilePath());
        newSubDto.setModelName(dto.getName());
        return createInitial(modelVersionId, newSubDto);
    }

    public ModelSubDTO createFedInitial(Long modelVersionId, Long experimentRunId) {
        Optional<ModelSubEntity> existingEntity = ao.findByModelVersionAndFedEx(modelVersionId, experimentRunId);
        if (existingEntity.isPresent()) {
            return mapper.entityToDto(existingEntity.get());
        }
        ModelSubDTO newSubDto = new ModelSubDTO();
        newSubDto.setFederatedExperimentId(experimentRunId);
        return createInitial(modelVersionId, newSubDto);
    }

    public ModelSubDTO createInitial(Long modelVersionId, ModelSubDTO newSubDto) {
        newSubDto.setModelVersionId(modelVersionId);
        newSubDto.setStatus(ModelSubStatus.INITIALIZED);
        ModelSubEntity entity = mapper.dtoToEntity(newSubDto);
        ao.persist(entity);
        return mapper.entityToDto(entity);
    }


    public ModelSubDTO update(bio.cosy.feddb.core.api.model.ModelSubDataDTO dto, Long modelVersionId) {
        Optional<ModelSubEntity> entity = ao.findByModelVersionAndEx(modelVersionId, dto.getRunId());
        if (entity.isEmpty()) {
            return createInitial(modelVersionId, dto);
        }
        ModelSubEntity modelSubEntity = entity.get();
        modelSubEntity.setStatus(ModelSubStatus.TRAINING);
        modelSubEntity.setModelPath(dto.getFilePath());
        modelSubEntity.setModelName(dto.getName());
        ao.persist(modelSubEntity);
        return mapper.entityToDto(modelSubEntity);
    }

    public void addModelSubData(ModelSubDTO subDto, bio.cosy.feddb.core.api.model.ModelSubDataDTO data, String keycloakId) {
        File file = data.getFile().uploadedFile().toFile();
        modelSubFileBO.create(file, subDto.getId(), data.getFilePath(),keycloakId);
    }

    public ModelSubDTO getByRunId(Long id, String keycloakId) {
        Optional<ModelSubEntity> entity = ao.findByRunId(id);
        if (entity.isEmpty()) {
            throw new NotFoundException("ModelSub not found");
        }
        if (accessBO.hasUserAnyRights(keycloakId, entity.get().getModelVersion().getModel().getId())) {
            return mapper.entityToDto(entity.get());
        }
        throw new NotFoundException("ModelSub not found (no rights)");
    }

    public ModelSubDTO getByFederatedRunId(Long id, String keycloakId) {
        Optional<ModelSubEntity> entity = ao.findByFederatedRunId(id);
        if (entity.isEmpty()) {
            throw new NotFoundException("ModelSub not found");
        }
        if (accessBO.hasUserAnyRights(keycloakId, entity.get().getModelVersion().getModel().getId())) {
            return mapper.entityToDto(entity.get());
        }
        throw new NotFoundException("ModelSub not found (no rights)");
    }

    public ModelSubDTO findBySubIdOrModelVersionId(Long modelSubId, Long modelVersionId, String keycloakId) {
        Optional<ModelSubEntity> entity;
        if (modelSubId != null) {
            entity = ao.findByIdOptional(modelSubId);
        } else {
            entity = ao.findByRunModelVersionId(modelVersionId);
        }
        if (entity.isEmpty()) {
            throw new NotFoundException("ModelSub not found");
        }
        return mapper.entityToDto(entity.get());
    }


    public ModelSubDTO selectModel(Long modelSubId, String keycloakId) {
        ModelSubDTO modelSubDTO = getByRunId(modelSubId, keycloakId);
        if (modelSubDTO.getStatus() != ModelSubStatus.TRAINED) {
            throw new NotFoundException("You can only select a trained model");
        }
        modelPredictionAO.deleteByRunId(modelSubDTO.getModelVersionId(), modelSubId);
        ao.deleteByRunId(modelSubDTO.getModelVersionId(), modelSubId);
        return modelSubDTO;
    }
}
