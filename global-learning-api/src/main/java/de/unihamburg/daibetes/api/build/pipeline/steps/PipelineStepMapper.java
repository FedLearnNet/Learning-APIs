package de.unihamburg.daibetes.api.build.pipeline.steps;

import bio.cosy.feddb.core.base.BaseMapper;
import de.unihamburg.daibetes.helper.QuarkusMappingConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Mappings;

@Mapper(config = QuarkusMappingConfig.class)
public interface PipelineStepMapper extends BaseMapper<PipelineStepDTO, PipelineStepEntity> {


    @Mappings({
            @Mapping(target = "pipelineId", source = "pipeline.id")
    })
    PipelineStepDTO entityToDto(PipelineStepEntity entity);

    @Mappings({
            @Mapping(target = "pipeline.id", source = "pipelineId"),
    })
    PipelineStepEntity dtoToEntity(PipelineStepDTO dto);

}
