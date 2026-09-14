package de.unihamburg.daibetes.api.model;

import bio.cosy.feddb.core.api.app.FederatedAppDTO;
import bio.cosy.feddb.core.api.app.FederatedAppDetailDTO;
import bio.cosy.feddb.core.api.model.ModelDTO;
import bio.cosy.feddb.core.api.model.ModelDetailDTO;
import bio.cosy.feddb.core.api.model.ModelPublishStatus;
import bio.cosy.feddb.core.base.BaseMapper;
import de.unihamburg.daibetes.api.app.FederatedAppEntity;
import de.unihamburg.daibetes.api.app.FederatedAppMapper;
import bio.cosy.feddb.core.api.model.ModelVersionDTO;
import de.unihamburg.daibetes.api.model.version.ModelVersionEntity;
import de.unihamburg.daibetes.api.model.version.ModelVersionMapper;
import de.unihamburg.daibetes.helper.QuarkusMappingConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Mappings;
import org.mapstruct.Named;
import org.mapstruct.factory.Mappers;

import java.util.Set;


@Mapper(config = QuarkusMappingConfig.class)
public interface ModelMapper extends BaseMapper<ModelDTO, ModelEntity> {

    FederatedAppMapper appMapper = Mappers.getMapper(FederatedAppMapper.class);
    ModelVersionMapper modelVersionMapper = Mappers.getMapper(ModelVersionMapper.class);


    @Mappings({
            @Mapping(target = "federatedAppId", source = "federatedAppVersion.federatedApp.id"),
            @Mapping(target = "federatedAppVersionId", source = "federatedAppVersion.id"),
            @Mapping(target = "federatedApp", source = "federatedAppVersion.federatedApp", qualifiedByName = "mapApp"),
            @Mapping(target = "lastVersion", source = "versions", qualifiedByName = "lastVersion"),
    })
    ModelDTO entityToDto(ModelEntity entity);

    @Mappings({
            @Mapping(target = "versions", ignore = true),
            @Mapping(target = "accesses", ignore = true),
            @Mapping(target = "federatedAppVersion.id", source = "federatedAppVersionId"),
    })
    ModelEntity dtoToEntity(ModelDTO dto);


    @Mappings({
            @Mapping(target = "modelVersions", ignore = true),
            @Mapping(target = "accesses", ignore = true),
            @Mapping(target = "createdByUser", ignore = true),
            @Mapping(target = "creator", ignore = true)
    })
    ModelDetailDTO toDetail(ModelDTO dto);

    @Named("mapApp")
    default FederatedAppDetailDTO mapApp(FederatedAppEntity app) {
        FederatedAppDTO dto = appMapper.entityToDto(app);
        return appMapper.dtoToDetailDto(dto);
    }

    @Named("lastVersion")
    default ModelVersionDTO lastVersion(Set<ModelVersionEntity> versions) {
        if (versions == null || versions.isEmpty()) return null;
        return versions.stream()
                .filter(v -> v.getPublishStatus().equals(ModelPublishStatus.PUBLISHED))
                .sorted((v1, v2) -> {
                    int majorCompare = v2.getMajorVersion().compareTo(v1.getMajorVersion());
                    if (majorCompare != 0) return majorCompare;
                    int minorCompare = v2.getMinorVersion().compareTo(v1.getMinorVersion());
                    if (minorCompare != 0) return minorCompare;
                    return v2.getPatchVersion().compareTo(v1.getPatchVersion());
                })
                .map(modelVersionMapper::entityToDto)
                .findFirst().orElse(null);

    }

}
