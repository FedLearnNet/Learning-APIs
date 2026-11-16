package de.unihamburg.daibetes.api.project.training.result.response;

import bio.cosy.feddb.core.base.BaseMapper;
import de.unihamburg.daibetes.helper.QuarkusMappingConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = QuarkusMappingConfig.class)
public interface RunResultResponseMapper extends BaseMapper<RunResultResponseDTO, RunResultResponseEntity> {

    @Mapping(target = "requestId", source = "request.id")
    RunResultResponseDTO entityToDto(RunResultResponseEntity entity);

    @Mapping(target = "request.id", source = "requestId")
    RunResultResponseEntity dtoToEntity(RunResultResponseDTO dto);
}
