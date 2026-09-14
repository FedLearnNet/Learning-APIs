package de.unihamburg.daibetes.api.model.version;

import bio.cosy.feddb.core.api.model.ModelVersionDTO;
import bio.cosy.feddb.core.base.BaseBo;
import bio.cosy.feddb.core.api.model.ModelPublishStatus;
import bio.cosy.feddb.core.api.model.ModelSubDTO;
import de.unihamburg.daibetes.api.model.access.ModelAccessBO;
import de.unihamburg.daibetes.api.model.sub.ModelSubBO;
import de.unihamburg.daibetes.api.model.sub.ModelSubEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotAllowedException;
import jakarta.ws.rs.NotFoundException;

import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.Set;


@ApplicationScoped
public class ModelVersionBO extends BaseBo<ModelVersionDTO, ModelVersionEntity, ModelVersionAO, ModelVersionMapper> {

    @Inject
    ModelAccessBO accessBO;

    @Inject
    ModelSubBO modelSubBO;

    public void checkExistsById(Long id) {
        if (!ao.existsById(id)) {
            throw new NotFoundException("Model Version not found");
        }
    }

    public Optional<ModelVersionDTO> findLastUnpublishedVersionByExperimentId(Long experimentId) {
        return this.ao.findLastUnpublishedVersionByExperimentId(experimentId).map(this.mapper::entityToDto);
    }

    public Optional<ModelVersionDTO> findLastUnpublishedVersionByFederatedExperimentId(Long experimentId) {
        return this.ao.findLastUnpublishedVersionByFederatedExperimentId(experimentId).map(this.mapper::entityToDto);
    }

    public ModelVersionDTO update(Long id, Long modelVersionId, ModelVersionDTO request, String keycloakId) {
        if (!id.equals(modelVersionId)) {
            throw new NotAllowedException("ID in path and body do not match");
        }
        if (accessBO.hasUserAnyRights(keycloakId, request.getModelId())) {
            ModelVersionEntity e = this.ao.findById(modelVersionId);
            e.setMajorVersion(request.getModelVersion());
            e.setMinorVersion(request.getModelVersion());
            e.setPatchVersion(request.getModelVersion());
            e.setChangelog(request.getChangelog());
            e.setPublishStatus(request.getPublishStatus());
            e.setUpdatedAt(new Date());
            this.ao.persist(e);
            return this.mapper.entityToDto(e);
        }
        throw new NotAllowedException("User does not have access to this model");
    }

    public ModelVersionDTO getByExperiment(Long id, String keycloakId) {
        Optional<ModelVersionDTO> dto = findLastUnpublishedVersionByExperimentId(id);
        if (dto.isEmpty()) {
            throw new NotFoundException("ModelVersion not found");
        }
        if (accessBO.hasUserAnyRights(keycloakId, dto.get().getModelId())) {
            return dto.get();
        }
        throw new NotFoundException("ModelVersion not found (no rights)");
    }

    public ModelVersionDTO getByFederatedExperiment(Long id, String keycloakId) {
        Optional<ModelVersionDTO> dto = findLastUnpublishedVersionByFederatedExperimentId(id);
        if (dto.isEmpty()) {
            throw new NotFoundException("ModelVersion not found");
        }
        if (accessBO.hasUserAnyRights(keycloakId, dto.get().getModelId())) {
            return dto.get();
        }
        throw new NotFoundException("ModelVersion not found (no rights)");
    }


    public List<ModelVersionDTO> getAllForModel(Long modelId) {
        return this.ao.getAllForModel(modelId).stream()
                .map(this::entityToStoreDto)
                .toList();
    }

    public List<ModelVersionDTO> getPublicForModel(Long modelId) {
        return this.ao.getPublicForModel(modelId).stream()
                .map(this::entityToStoreDto)
                .toList();
    }

    private ModelVersionDTO entityToStoreDto(ModelVersionEntity entity) {
        ModelVersionDTO dto = mapper.entityToDto(entity);
        Set<ModelSubEntity> subModelEntities = entity.getSubModels();
        if (subModelEntities == null || subModelEntities.isEmpty()) {
            dto.setSubModels(Collections.emptyList());
            dto.setSelectedSubModel(null);
            return dto;
        }

        List<ModelSubDTO> subModels = subModelEntities.stream()
                .map(modelSubBO::entityToStoreDto)
                .toList();
        dto.setSubModels(subModels);
        dto.setSelectedSubModel(subModels.size() == 1 ? subModels.getFirst() : null);
        return dto;
    }

    public ModelVersionDTO createInitial(Long modelId, Long experimentId) {
        ModelVersionDTO newModelVersion = new ModelVersionDTO();
        newModelVersion.setExperimentId(experimentId);
        return createInitial(modelId, newModelVersion);
    }

    public ModelVersionDTO createInitialFederated(Long modelId, Long experimentId) {
        Optional<ModelVersionEntity> existingEntity = ao.findByModelAndFedEx(modelId, experimentId);
        if (existingEntity.isPresent()) {
            return mapper.entityToDto(existingEntity.get());
        }
        ModelVersionDTO newModelVersion = new ModelVersionDTO();
        newModelVersion.setFederatedExperimentId(experimentId);
        return createInitial(modelId, newModelVersion);
    }

    public ModelVersionDTO createInitial(Long modelId, ModelVersionDTO newModelVersion) {
        newModelVersion.setModelId(modelId);
        newModelVersion.setModelVersion("0.0.0");
        newModelVersion.setChangelog("Initial version");
        newModelVersion.setPublishStatus(ModelPublishStatus.PRIVATE);
        return create(newModelVersion);
    }


    public ModelVersionDTO createInitial(Long modelId) {
        ModelVersionDTO newModelVersion = new ModelVersionDTO();
        newModelVersion.setModelId(modelId);
        newModelVersion.setModelVersion("0.0.0");
        newModelVersion.setChangelog("Initial version");
        newModelVersion.setPublishStatus(ModelPublishStatus.PRIVATE);
        ModelVersionEntity entity = mapper.dtoToEntity(newModelVersion);
        entity.setExperiment(null);
        ao.persist(entity);
        ModelVersionDTO dto = mapper.entityToDto(entity);
        return dto;
    }
}
