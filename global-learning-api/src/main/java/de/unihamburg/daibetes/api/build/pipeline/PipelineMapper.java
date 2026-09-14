package de.unihamburg.daibetes.api.build.pipeline;

import bio.cosy.feddb.core.base.BaseMapper;
import de.unihamburg.daibetes.api.app.version.FederatedAppVersionEntity;
import de.unihamburg.daibetes.api.build.pipeline.steps.PipelineStepMapper;
import de.unihamburg.daibetes.api.model.sub.ModelSubEntity;
import de.unihamburg.daibetes.helper.QuarkusMappingConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Mappings;
import org.mapstruct.Named;
import org.mapstruct.factory.Mappers;

@Mapper(config = QuarkusMappingConfig.class)
public interface PipelineMapper extends BaseMapper<PipelineDTO, PipelineEntity> {

    PipelineStepMapper stepMapper = Mappers.getMapper(PipelineStepMapper.class);


    @Mappings({
            @Mapping(target = "pipelineSteps", expression = "java(stepMapper.entitiesToDtos(entity.getSteps()))"),
            @Mapping(target = "appVersionId", source = "entity", qualifiedByName = "getAppVersionId"),
            @Mapping(target = "modelSubId", source = "entity", qualifiedByName = "getModelSubId")
    })
    PipelineDTO entityToDto(PipelineEntity entity);

    @Mappings({
            @Mapping(target = "steps", ignore = true),
            @Mapping(target = "appVersion", source = "dto.appVersionId", qualifiedByName = "getAppVersion"),
            @Mapping(target = "modelSub", source = "dto.modelSubId", qualifiedByName = "getModelSub")
    })
    PipelineEntity dtoToEntity(PipelineDTO dto);


    @Named("getAppVersion")
    default FederatedAppVersionEntity getAppVersion(Long id) {
        if (id == null) {
            return null;
        }
        FederatedAppVersionEntity entity = new FederatedAppVersionEntity();
        entity.setId(id);
        return entity;
    }

    @Named("getModelSub")
    default ModelSubEntity getModelSub(Long id) {
        if (id == null) {
            return null;
        }
        ModelSubEntity entity = new ModelSubEntity();
        entity.setId(id);
        return entity;
    }

    @Named("getAppVersionId")
    default Long getAppVersionId(PipelineEntity entity) {
        if (entity == null) {
            return null;
        }
        if (entity.getAppVersion() == null) {
            return null;
        }
        return entity.getAppVersion().getId();
    }

    @Named("getModelSubId")
    default Long getModelSubId(PipelineEntity entity) {
        if (entity == null) {
            return null;
        }
        if (entity.getModelSub() == null) {
            return null;
        }
        return entity.getModelSub().getId();
    }

}
