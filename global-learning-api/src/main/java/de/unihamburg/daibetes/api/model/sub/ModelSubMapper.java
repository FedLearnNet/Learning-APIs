package de.unihamburg.daibetes.api.model.sub;

import bio.cosy.feddb.core.api.model.ModelSubDTO;
import bio.cosy.feddb.core.base.BaseEntity;
import bio.cosy.feddb.core.base.BaseMapper;
import de.unihamburg.daibetes.api.build.pipeline.PipelineEntity;
import bio.cosy.feddb.core.api.pipeline.PipelineStatus;
import de.unihamburg.daibetes.api.model.sub.file.ModelSubFileMapper;
import de.unihamburg.daibetes.execution.experiment.ExecutionExperimentEntity;
import de.unihamburg.daibetes.execution.attempt.ExecutionAttemptEntity;
import de.unihamburg.daibetes.helper.QuarkusMappingConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Mappings;
import org.mapstruct.Named;
import org.mapstruct.factory.Mappers;


@Mapper(config = QuarkusMappingConfig.class)
public interface ModelSubMapper extends BaseMapper<ModelSubDTO, ModelSubEntity> {

    ModelSubFileMapper fileMapper = Mappers.getMapper(ModelSubFileMapper.class);

    @Mappings({
            @Mapping(target = "modelVersionId", source = "modelVersion.id"),
            @Mapping(target = "modelId", source = "modelVersion.model.id"),
            @Mapping(target = "experimentRunId", source = "experimentRun", qualifiedByName = "mapExperimentId"),
            @Mapping(target = "federatedExperimentId", source = "federatedExperiment", qualifiedByName = "mapFedExperimentId"),
            @Mapping(target = "files", expression = "java(fileMapper.entitiesToDtos(entity.getFiles()))"),
            @Mapping(target = "pipelineId", source = "entity", qualifiedByName = "mapPipelineId"),
            @Mapping(target = "pipelineStatus", source = "entity", qualifiedByName = "mapPipelineStatus"),
            @Mapping(target = "federatedData", ignore = true)
    })
    ModelSubDTO entityToDto(ModelSubEntity entity);

    @Mappings({
            @Mapping(target = "modelVersion.id", source = "modelVersionId"),
            @Mapping(target = "experimentRun", source = "experimentRunId", qualifiedByName = "mapExperiment"),
            @Mapping(target = "federatedExperiment", source = "federatedExperimentId", qualifiedByName = "mapFedExperiment"),
            @Mapping(target = "files", ignore = true),
            @Mapping(target = "pipelines", ignore = true)
    })
    ModelSubEntity dtoToEntity(ModelSubDTO dto);


    @Named("mapPipelineId")
    default Long mapPipelineId(ModelSubEntity entity) {
        if (entity == null || entity.getPipelines() == null || entity.getPipelines().isEmpty()) {
            return null;
        }
        return entity.getPipelines().stream().findFirst().map(BaseEntity::getId).orElse(null);
    }

    @Named("mapPipelineStatus")
    default PipelineStatus mapPipelineStatus(ModelSubEntity entity) {
        if (entity == null || entity.getPipelines() == null || entity.getPipelines().isEmpty()) {
            return null;
        }
        return entity.getPipelines().stream().findFirst().map(PipelineEntity::getPipelineStatus).orElse(null);
    }

    @Named("mapExperiment")
    default ExecutionAttemptEntity mapExperiment(Long experimentId) {
        if (experimentId == null) {
            return null;
        }
        ExecutionAttemptEntity experiment = new ExecutionAttemptEntity();
        experiment.setId(experimentId);
        return experiment;
    }

    @Named("mapFedExperiment")
    default ExecutionExperimentEntity mapFedExperiment(Long experimentId) {
        if (experimentId == null) {
            return null;
        }
        ExecutionExperimentEntity experiment = new ExecutionExperimentEntity();
        experiment.setId(experimentId);
        return experiment;
    }

    @Named("mapExperimentId")
    default Long mapExperimentId(ExecutionAttemptEntity experiment) {
        if (experiment == null) {
            return null;
        }
        return experiment.getId();
    }

    @Named("mapFedExperimentId")
    default Long mapFedExperimentId(ExecutionExperimentEntity experiment) {
        if (experiment == null) {
            return null;
        }
        return experiment.getId();
    }

}
