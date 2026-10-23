package de.unihamburg.daibetes.execution.message;

import bio.cosy.feddb.core.base.BaseMapper;
import bio.cosy.feddb.core.execution.message.ExecutionMessageDTO;
import de.unihamburg.daibetes.helper.QuarkusMappingConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = QuarkusMappingConfig.class)
public interface ExecutionMessageMapper extends BaseMapper<ExecutionMessageDTO, ExecutionMessageEntity> {

    @Mapping(target = "runId", source = "attempt.id")
    ExecutionMessageDTO entityToDto(ExecutionMessageEntity entity);

    @Mapping(target = "attempt.id", source = "runId")
    ExecutionMessageEntity dtoToEntity(ExecutionMessageDTO dto);
}
