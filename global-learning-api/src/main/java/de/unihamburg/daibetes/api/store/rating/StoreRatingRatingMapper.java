package de.unihamburg.daibetes.api.store.rating;


import bio.cosy.feddb.core.api.store.StoreRatingDTO;
import bio.cosy.feddb.core.base.BaseMapper;
import de.unihamburg.daibetes.api.app.FederatedAppEntity;
import de.unihamburg.daibetes.api.model.version.ModelVersionEntity;
import de.unihamburg.daibetes.helper.QuarkusMappingConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Mappings;
import org.mapstruct.Named;

@Mapper(config = QuarkusMappingConfig.class)
public interface StoreRatingRatingMapper extends BaseMapper<StoreRatingDTO, StoreRatingEntity> {

    @Mappings({
            @Mapping(target = "federatedAppId", source = "entity", qualifiedByName = "getAppId"),
            @Mapping(target = "modelVersionId", source = "entity", qualifiedByName = "getModelVersionId"),
    })
    StoreRatingDTO entityToDto(StoreRatingEntity entity);

    @Mappings({
            @Mapping(target = "federatedApp", source = "federatedAppId", qualifiedByName = "getApp"),
            @Mapping(target = "modelVersion", source = "modelVersionId", qualifiedByName = "getModelVersion"),
    })
    StoreRatingEntity dtoToEntity(StoreRatingDTO sourceCode);

    @Mappings({
            @Mapping(target = "version", ignore = true),
            @Mapping(target = "updatedAt", ignore = true),
            @Mapping(target = "keycloakId", ignore = true),
            @Mapping(target = "id", ignore = true),
            @Mapping(target = "federatedAppId", ignore = true),
            @Mapping(target = "createdAt", ignore = true),
            @Mapping(target = "modelVersionId", ignore = true)
    })
    StoreRatingDTO createDtoToEntity(StoreRatingCreateDTO sourceCode);

    @Named("getApp")
    default FederatedAppEntity getAppVersion(Long id) {
        if (id == null) {
            return null;
        }
        FederatedAppEntity entity = new FederatedAppEntity();
        entity.setId(id);
        return entity;
    }

    @Named("getModelVersion")
    default ModelVersionEntity getModelVersion(Long id) {
        if (id == null) {
            return null;
        }
        ModelVersionEntity entity = new ModelVersionEntity();
        entity.setId(id);
        return entity;
    }

    @Named("getAppId")
    default Long getAppVersionId(StoreRatingEntity entity) {
        if (entity == null) {
            return null;
        }
        if (entity.getFederatedApp() == null) {
            return null;
        }
        return entity.getFederatedApp().getId();
    }

    @Named("getModelVersionId")
    default Long getModelVersionId(StoreRatingEntity entity) {
        if (entity == null) {
            return null;
        }
        if (entity.getModelVersion() == null) {
            return null;
        }
        return entity.getModelVersion().getId();
    }
}
