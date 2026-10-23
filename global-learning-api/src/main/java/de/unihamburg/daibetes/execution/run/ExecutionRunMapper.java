package de.unihamburg.daibetes.execution.run;

import bio.cosy.feddb.core.execution.run.BaseExecutionEntityMapper;
import de.unihamburg.daibetes.execution.step.ExecutionRunStepMapper;
import de.unihamburg.daibetes.helper.QuarkusMappingConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = QuarkusMappingConfig.class, uses = ExecutionRunStepMapper.class)
public interface ExecutionRunMapper extends BaseExecutionEntityMapper<ExecutionRunDTO, ExecutionRunEntity> {

    ExecutionRunDTO entityToDto(ExecutionRunEntity entity);

    // Steps are created with the run, never taken over from a request.
    @Mapping(target = "steps", ignore = true)
    ExecutionRunEntity dtoToEntity(ExecutionRunDTO dto);
}
