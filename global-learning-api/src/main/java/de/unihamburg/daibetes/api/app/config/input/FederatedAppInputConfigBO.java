package de.unihamburg.daibetes.api.app.config.input;

import bio.cosy.feddb.core.api.app.FederatedAppType;
import bio.cosy.feddb.core.api.app.config.ToolInputConfigDTO;
import de.unihamburg.daibetes.api.app.config.ToolTypeConfigPolicy;
import de.unihamburg.daibetes.api.app.version.FederatedAppVersionAO;
import de.unihamburg.daibetes.api.app.version.FederatedAppVersionEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.*;

@ApplicationScoped
public class FederatedAppInputConfigBO {

    @Inject
    public FederatedAppInputConfigAO ao;

    @Inject
    FederatedAppVersionAO appVersionAo;

    @Inject
    public FederatedAppInputConfigMapper mapper;


    public List<ToolInputConfigDTO> getAll(Long appId) {
        return mapper.entitiesToDtos(ao.findByAppVersionId(appId).stream());
    }


    public List<ToolInputConfigDTO> createOrUpdate(Long appId, List<ToolInputConfigDTO> dtos, FederatedAppType appType) {
        Optional<FederatedAppVersionEntity> appOptional = appVersionAo.findByIdOptional(appId);
        if (appOptional.isEmpty()) {
            return new ArrayList<>();
        }
        ToolTypeConfigPolicy.checkNeedsInput(appType);

        FederatedAppVersionEntity app = appOptional.get();
        List<Long> updatedIds = new ArrayList<>();
        List<ToolInputConfigDTO> persisted = dtos.stream()
                .map(dto -> {
                    Optional<FederatedAppInputConfigEntity> configEntity = ao.findByNameAndAppVersionId(appId, dto.getName());
                    if (configEntity.isEmpty() && dto.getId() == null) {
                        return create(dto, app);
                    } else {
                        if (configEntity.isPresent() && !updatedIds.contains(configEntity.get().getId())) {
                            if(ToolTypeConfigPolicy.canOnlyEditSchema(appType)){
                                updatedIds.add(configEntity.get().getId());
                                return updateSchema(dto, configEntity.get(), app);
                            }
                            ToolTypeConfigPolicy.checkInputConfigUpdateAllowed(appType);
                            updatedIds.add(configEntity.get().getId());
                            return update(dto, configEntity.get(), app);
                        }
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .toList();
        List<Long> persistedIds = persisted.stream().map(ToolInputConfigDTO::getId).toList();
        ao.deleteNotIn(persistedIds, appId);
        return persisted;
    }

    public List<ToolInputConfigDTO> create(FederatedAppVersionEntity app, List<ToolInputConfigDTO> dtos) {
        if (dtos == null || dtos.isEmpty()) {
            return new ArrayList<>();
        }
        return dtos.stream()
                .map(dto -> create(dto, app))
                .filter(Objects::nonNull)
                .toList();
    }

    private ToolInputConfigDTO create(ToolInputConfigDTO dto, FederatedAppVersionEntity appVersion) {
        FederatedAppInputConfigEntity entity = mapper.dtoToEntity(dto);
        entity.setFederatedAppVersion(appVersion);
        if (ao.isPersistent(entity)) {
            ao.getEntityManager().merge(entity);
        } else {
            ao.persist(entity);
        }
        return mapper.entityToDto(entity);
    }

    private ToolInputConfigDTO update(ToolInputConfigDTO dto, FederatedAppInputConfigEntity foundEntity, FederatedAppVersionEntity appVersion) {
        FederatedAppInputConfigEntity entity = mapper.dtoToEntity(dto);

        entity.setUpdatedAt(new Date());
        entity.setId(foundEntity.getId());
        entity.setCreatedAt(foundEntity.getCreatedAt());
        entity.setVersion(foundEntity.getVersion());
        entity.setFederatedAppVersion(appVersion);

        FederatedAppInputConfigEntity mergedEntity = ao.getEntityManager().merge(entity);
        return mapper.entityToDto(mergedEntity);
    }

    private ToolInputConfigDTO updateSchema(ToolInputConfigDTO dto, FederatedAppInputConfigEntity foundEntity, FederatedAppVersionEntity appVersion) {
        foundEntity.setTabularSchema(dto.getTabularSchema());
        ao.persist(foundEntity);
        return mapper.entityToDto(foundEntity);
    }

    public FederatedAppInputConfigEntity cloneEntity(FederatedAppInputConfigEntity entity, FederatedAppVersionEntity existingVersion) {
        ToolInputConfigDTO dto = mapper.entityToDto(entity);
        dto.setId(null);
        FederatedAppInputConfigEntity clone = mapper.dtoToEntity(dto);
        clone.setFederatedAppVersion(existingVersion);
        ao.persist(clone);
        return clone;
    }
}

