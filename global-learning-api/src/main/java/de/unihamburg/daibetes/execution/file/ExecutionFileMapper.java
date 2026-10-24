package de.unihamburg.daibetes.execution.file;

import bio.cosy.feddb.core.execution.file.BaseExecutionFileMapper;
import de.unihamburg.daibetes.api.file.FileMapper;
import de.unihamburg.daibetes.helper.QuarkusMappingConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Mappings;

@Mapper(config = QuarkusMappingConfig.class, uses = FileMapper.class)
public interface ExecutionFileMapper extends BaseExecutionFileMapper<ExecutionFileDTO, ExecutionFileEntity> {

    @Mappings({
            @Mapping(target = "attemptId", source = "attempt.id"),
            @Mapping(target = "content", ignore = true)
    })
    ExecutionFileDTO entityToDto(ExecutionFileEntity entity);

    @Mappings({
            @Mapping(target = "attempt.id", source = "attemptId"),
            @Mapping(target = "file", ignore = true)
    })
    ExecutionFileEntity dtoToEntity(ExecutionFileDTO dto);
}
