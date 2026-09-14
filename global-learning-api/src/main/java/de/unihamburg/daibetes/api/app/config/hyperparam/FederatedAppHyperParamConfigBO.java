package de.unihamburg.daibetes.api.app.config.hyperparam;

import bio.cosy.feddb.core.api.app.config.ToolHyperParamConfigDTO;
import de.unihamburg.daibetes.api.app.version.FederatedAppVersionAO;
import de.unihamburg.daibetes.api.app.version.FederatedAppVersionEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.*;

@ApplicationScoped
public class FederatedAppHyperParamConfigBO {

    @Inject
    public FederatedAppHyperParamConfigAO ao;

    @Inject
    FederatedAppVersionAO appVersionAo;

    @Inject
    public FederatedAppHyperParamConfigMapper mapper;


    public List<ToolHyperParamConfigDTO> getAll(Long versionId) {
        return mapper.entitiesToDtos(ao.findByAppVersionId(versionId).stream());
    }


    public List<ToolHyperParamConfigDTO> createOrUpdate(Long versionId, List<ToolHyperParamConfigDTO> dtos) {
        Optional<FederatedAppVersionEntity> appOptional = appVersionAo.findByIdOptional(versionId);
        if (appOptional.isEmpty()) {
            return new ArrayList<>();
        }
        FederatedAppVersionEntity app = appOptional.get();
        List<Long> updatedIds = new ArrayList<>();
        List<ToolHyperParamConfigDTO> persisted = dtos.stream()
                .map(dto -> {
                    Optional<FederatedAppHyperParamConfigEntity> configEntity = ao.findByNameAndAppVersionId(versionId, dto.getName());
                    if (configEntity.isEmpty() && dto.getId() == null) {
                        return create(dto, app);
                    } else {
                        if (configEntity.isPresent() && !updatedIds.contains(configEntity.get().getId())) {
                            updatedIds.add(configEntity.get().getId());
                            return update(dto, configEntity.get(), app);
                        }
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .toList();
        List<Long> persistedIds = persisted.stream().map(ToolHyperParamConfigDTO::getId).toList();
        ao.deleteNotIn(persistedIds, versionId);
        return persisted;
    }

    public List<ToolHyperParamConfigDTO> create(FederatedAppVersionEntity app, List<ToolHyperParamConfigDTO> dtos) {
        if(dtos == null || dtos.isEmpty()) {
            return new ArrayList<>();
        }
        return dtos.stream()
                .map(dto -> create(dto, app))
                .filter(Objects::nonNull)
                .toList();
    }

    private ToolHyperParamConfigDTO create(ToolHyperParamConfigDTO dto, FederatedAppVersionEntity version) {
        FederatedAppHyperParamConfigEntity entity = mapper.dtoToEntity(dto);
        entity.setFederatedAppVersion(version);
        if (ao.isPersistent(entity)) {
            ao.getEntityManager().merge(entity);
        } else {
            ao.persist(entity);
        }
        return mapper.entityToDto(entity);
    }

    private ToolHyperParamConfigDTO update(ToolHyperParamConfigDTO dto, FederatedAppHyperParamConfigEntity foundEntity, FederatedAppVersionEntity version) {
        FederatedAppHyperParamConfigEntity entity = mapper.dtoToEntity(dto);

        entity.setUpdatedAt(new Date());
        entity.setId(foundEntity.getId());
        entity.setCreatedAt(foundEntity.getCreatedAt());
        entity.setVersion(foundEntity.getVersion());
        entity.setFederatedAppVersion(version);

        FederatedAppHyperParamConfigEntity mergedEntity = ao.getEntityManager().merge(entity);
        return mapper.entityToDto(mergedEntity);
    }

    public FederatedAppHyperParamConfigEntity cloneEntity(FederatedAppHyperParamConfigEntity entity, FederatedAppVersionEntity existingVersion) {
        ToolHyperParamConfigDTO dto = mapper.entityToDto(entity);
        dto.setId(null);
        FederatedAppHyperParamConfigEntity clone = mapper.dtoToEntity(dto);
        clone.setFederatedAppVersion(existingVersion);
        ao.persist(clone);
        return clone;
    }
}
