package de.unihamburg.daibetes.api.app.config.output;

import bio.cosy.feddb.core.api.app.FederatedAppType;
import bio.cosy.feddb.core.api.app.config.ToolOutputConfigDTO;
import de.unihamburg.daibetes.api.app.config.ToolTypeConfigPolicy;
import de.unihamburg.daibetes.api.app.version.FederatedAppVersionAO;
import de.unihamburg.daibetes.api.app.version.FederatedAppVersionEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.*;

@ApplicationScoped
public class FederatedAppOutputConfigBO {

    @Inject
    FederatedAppOutputConfigAO ao;

    @Inject
    FederatedAppVersionAO appVersionAo;

    @Inject
    FederatedAppOutputConfigMapper mapper;


    public List<ToolOutputConfigDTO> getAll(Long appId) {
        return mapper.entitiesToDtos(ao.findByAppVersionId(appId).stream());
    }


    public List<ToolOutputConfigDTO> createOrUpdate(Long appId, List<ToolOutputConfigDTO> dtos, FederatedAppType appType) {
        Optional<FederatedAppVersionEntity> appOptional = appVersionAo.findByIdOptional(appId);
        if (appOptional.isEmpty()) {
            return new ArrayList<>();
        }
        ToolTypeConfigPolicy.checkNeedsOutput(appType);
        FederatedAppVersionEntity app = appOptional.get();
        List<Long> updatedIds = new ArrayList<>();
        List<ToolOutputConfigDTO> persisted = dtos.stream()
                .map(dto -> {
                    Optional<FederatedAppOutputConfigEntity> configEntity = ao.findByNameAndAppVersionId(appId, dto.getName());
                    if (configEntity.isEmpty() && dto.getId() == null) {
                        return create(dto, app);
                    } else {
                        if (configEntity.isPresent() && !updatedIds.contains(configEntity.get().getId())) {
                            if (ToolTypeConfigPolicy.canOnlyEditSchema(appType)) {
                                updatedIds.add(configEntity.get().getId());
                                return updateSchema(dto, configEntity.get(), app);
                            }
                            ToolTypeConfigPolicy.checkOutputConfigUpdateAllowed(appType);
                            updatedIds.add(configEntity.get().getId());
                            return update(dto, configEntity.get(), app);
                        }
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .toList();
        List<Long> persistedIds = persisted.stream().map(ToolOutputConfigDTO::getId).toList();

        ao.deleteNotIn(persistedIds, appId);
        return persisted;
    }

    public List<ToolOutputConfigDTO> create(FederatedAppVersionEntity app, List<ToolOutputConfigDTO> dtos) {
        if (dtos == null || dtos.isEmpty()) {
            return new ArrayList<>();
        }
        return dtos.stream()
                .map(dto -> create(dto, app))
                .filter(Objects::nonNull)
                .toList();
    }

    private ToolOutputConfigDTO create(ToolOutputConfigDTO dto, FederatedAppVersionEntity appVersion) {
        FederatedAppOutputConfigEntity entity = mapper.dtoToEntity(dto);
        entity.setFederatedAppVersion(appVersion);
        if (ao.isPersistent(entity)) {
            ao.getEntityManager().merge(entity);
        } else {
            ao.persist(entity);
        }
        return mapper.entityToDto(entity);
    }

    private ToolOutputConfigDTO update(ToolOutputConfigDTO dto, FederatedAppOutputConfigEntity foundEntity, FederatedAppVersionEntity appVersion) {
        FederatedAppOutputConfigEntity entity = mapper.dtoToEntity(dto);

        entity.setUpdatedAt(new Date());
        entity.setId(foundEntity.getId());
        entity.setCreatedAt(foundEntity.getCreatedAt());
        entity.setVersion(foundEntity.getVersion());
        entity.setFederatedAppVersion(appVersion);

        FederatedAppOutputConfigEntity mergedEntity = ao.getEntityManager().merge(entity);
        return mapper.entityToDto(mergedEntity);
    }

    private ToolOutputConfigDTO updateSchema(ToolOutputConfigDTO dto, FederatedAppOutputConfigEntity foundEntity, FederatedAppVersionEntity appVersion) {
        foundEntity.setTabularSchema(dto.getTabularSchema());
        ao.persist(foundEntity);
        return mapper.entityToDto(foundEntity);
    }

    public FederatedAppOutputConfigEntity cloneEntity(FederatedAppOutputConfigEntity entity, FederatedAppVersionEntity existingVersion) {
        // Given the output config entity entity, create a clone of it and connects it to the provided AppVersionEntity existingVersion
        ToolOutputConfigDTO dto = mapper.entityToDto(entity);
        dto.setId(null);
        FederatedAppOutputConfigEntity clone = mapper.dtoToEntity(dto);
        clone.setFederatedAppVersion(existingVersion);
        ao.persist(clone);
        return clone;
    }
}
