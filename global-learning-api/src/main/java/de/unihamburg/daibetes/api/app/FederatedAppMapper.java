package de.unihamburg.daibetes.api.app;

import bio.cosy.feddb.core.api.app.FederatedAppDTO;
import bio.cosy.feddb.core.api.app.FederatedAppDetailDTO;
import bio.cosy.feddb.core.api.app.FederatedAppVersionDTO;
import bio.cosy.feddb.core.api.app.PublishStatus;
import bio.cosy.feddb.core.base.BaseMapper;
import de.unihamburg.daibetes.api.app.version.FederatedAppVersionEntity;
import de.unihamburg.daibetes.api.app.version.FederatedAppVersionMapper;
import de.unihamburg.daibetes.api.store.rating.StoreRatingEntity;
import de.unihamburg.daibetes.helper.QuarkusMappingConfig;
import org.mapstruct.*;
import org.mapstruct.factory.Mappers;

import java.util.Set;

@Mapper(config = QuarkusMappingConfig.class)
public interface FederatedAppMapper extends BaseMapper<FederatedAppDTO, FederatedAppEntity> {

    FederatedAppVersionMapper versionMapper = Mappers.getMapper(FederatedAppVersionMapper.class);

    @Mappings({
            @Mapping(target = "certificationLevel", ignore = true), // set in after mapping
            @Mapping(target = "imageName", ignore = true), // set in after mapping
            @Mapping(target = "latestVersion", ignore = true), // set in after mapping
            @Mapping(target = "latestVersionId", ignore = true), // set in after mapping
            @Mapping(target = "longDescription", ignore = true), // set in after mapping
            @Mapping(target = "shortDescription", ignore = true),
            @Mapping(target = "average", expression = "java(computeAverage(entity.getRatings()))"),
            @Mapping(target = "count", expression = "java(entity.getRatings() != null ? entity.getRatings().size() : 0)"),
            @Mapping(target = "publishStatus", ignore = true),
            @Mapping(target = "latestUnpublishedVersionId", ignore = true),
            @Mapping(target = "needsInternetAccess", ignore = true),
            @Mapping(target = "needsHostAccess", ignore = true),
            @Mapping(target = "publishInfo", ignore = true), // set in after mapping
    })
    FederatedAppDTO entityToDto(FederatedAppEntity entity);

    @Mappings({
            @Mapping(target = "authors", ignore = true),
            @Mapping(target = "ratings", ignore = true),
            @Mapping(target = "versions", ignore = true),
            @Mapping(target = "tags", ignore = true)
    })
    FederatedAppEntity dtoToEntity(FederatedAppDTO dto);

    @Mappings({
            @Mapping(target = "id", ignore = true),
            @Mapping(target = "createdAt", ignore = true),
            @Mapping(target = "updatedAt", ignore = true),
            @Mapping(target = "version", ignore = true),
            @Mapping(target = "uniqueAppId", ignore = true),
            @Mapping(target = "authors", ignore = true),
            @Mapping(target = "ratings", ignore = true),
            @Mapping(target = "versions", ignore = true),
            @Mapping(target = "tags", ignore = true)
    })
    void updateEntityFromDto(FederatedAppDTO dto, @MappingTarget FederatedAppEntity entity);

    @Mappings({
            @Mapping(target = "addedToWorkflowCount", ignore = true),
            @Mapping(target = "appConfig", ignore = true),
            @Mapping(target = "authors", ignore = true),
            @Mapping(target = "lastAddedToWorkflow", ignore = true),
            @Mapping(target = "versions", ignore = true),
    })
    FederatedAppDetailDTO dtoToDetailDto(FederatedAppDTO dto);

    @Mappings({
            @Mapping(target = "certificationLevel", ignore = true),
            @Mapping(target = "createdAt", ignore = true),
            @Mapping(target = "id", ignore = true),
            @Mapping(target = "latestVersion", ignore = true),
            @Mapping(target = "latestVersionId", ignore = true),
            @Mapping(target = "updatedAt", ignore = true),
            @Mapping(target = "version", ignore = true),
            @Mapping(target = "average", ignore = true),
            @Mapping(target = "count", ignore = true),
            @Mapping(target = "latestUnpublishedVersionId", ignore = true),
            @Mapping(target = "oldFCVersion", ignore = true),
            @Mapping(target = "imageName", ignore = true),
            @Mapping(target = "needsInternetAccess", ignore = true),
            @Mapping(target = "icon", ignore = true),
            @Mapping(target = "longDescription", ignore = true),
            @Mapping(target = "publishInfo", ignore = true),
            @Mapping(target = "publishStatus", ignore = true),
            @Mapping(target = "sourceUrl", ignore = true),
            @Mapping(target = "tags", ignore = true),
            @Mapping(target = "uniqueAppId", ignore = true),
            @Mapping(target = "needsHostAccess", ignore = true)
    })
    FederatedAppDTO createDtoToDto(AppCreateDTO dto);


    @AfterMapping
    default void setAppVersion(@MappingTarget FederatedAppDTO dto, FederatedAppEntity entity) {
        if (entity.getVersions() == null) {
            return;
        }
        FederatedAppVersionDTO lastVersion = lastVersion(entity.getVersions());
        this.setAppVersion(dto, lastVersion);
        dto.setLatestUnpublishedVersionId(entity
                .getVersions()
                .stream()
                .findFirst()
                .map(FederatedAppVersionEntity::getId)
                .orElse(null));
    }

    default void setAppVersion(FederatedAppDTO dto, FederatedAppVersionDTO lastVersion) {
        if (lastVersion == null) {
            return;
        }
        dto.setLongDescription(lastVersion.getLongDescription());
        dto.setShortDescription(lastVersion.getShortDescription());
        dto.setLatestVersion(lastVersion.getAppVersion());
        dto.setLatestVersionId(lastVersion.getId());
        dto.setImageName(lastVersion.getImageName());
        dto.setCertificationLevel(lastVersion.getCertificationLevel());
        dto.setPublishStatus(lastVersion.getVersionPublishStatus());
        dto.setNeedsInternetAccess(lastVersion.getNeedsInternetAccess());
        dto.setNeedsHostAccess(lastVersion.getNeedsHostAccess());
        dto.setPublishInfo(lastVersion.getPublishInfo());
    }

    default void updateVersion(FederatedAppDTO dto, Set<FederatedAppVersionEntity> versions) {
        FederatedAppVersionDTO version = versions.stream()
                .map(versionMapper::entityToDto)
                .findFirst().orElse(null);
        if (version == null) {
            return;
        }
        this.setAppVersion(dto, version);
    }


    default FederatedAppVersionDTO lastVersion(Set<FederatedAppVersionEntity> versions) {
        return versions.stream()
                .filter(v -> v.getVersionPublishStatus().equalsName(PublishStatus.PUBLISHED))
                .map(versionMapper::entityToDto)
                .findFirst().orElse(null);
    }

    default float computeAverage(Set<StoreRatingEntity> ratings) {
        if (ratings == null || ratings.isEmpty()) {
            return 0;
        }
        return (float) ratings.stream()
                .mapToDouble(StoreRatingEntity::getRating)
                .average()
                .orElse(0.0);
    }
}
