package bio.cosy.feddb.local.api.pipeline;

import bio.cosy.feddb.core.api.pipeline.PipelineStepDTO;
import bio.cosy.feddb.core.base.BaseMapper;
import bio.cosy.feddb.local.helper.QuarkusMappingConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = QuarkusMappingConfig.class)
public interface PipelineMapper extends BaseMapper<PipelineDTO, PipelineEntity> {

    @Mapping(target = "pipelineSteps", source = "steps")
    @Mapping(target = "codeTransformerId", source = "codeTransformer.id")
    PipelineDTO entityToDto(PipelineEntity entity);

    @Mapping(target = "pipelineId", source = "pipeline.id")
    PipelineStepDTO stepToDto(PipelineStepEntity entity);

    @Mapping(target = "secret", ignore = true)
    @Mapping(target = "steps", ignore = true)
    @Mapping(target = "codeTransformer", ignore = true)
    PipelineEntity dtoToEntity(PipelineDTO dto);
}
