package de.unihamburg.daibetes.execution.run;

import bio.cosy.feddb.core.execution.run.BaseExecutionEntityMapper;
import de.unihamburg.daibetes.execution.step.ExecutionRunStepMapper;
import de.unihamburg.daibetes.helper.QuarkusMappingConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Mappings;

@Mapper(config = QuarkusMappingConfig.class, uses = ExecutionRunStepMapper.class)
public interface ExecutionRunMapper extends BaseExecutionEntityMapper<ExecutionRunDTO, ExecutionRunEntity> {

    @Mapping(target = "experimentId", source = "experiment.id")
    ExecutionRunDTO entityToDto(ExecutionRunEntity entity);

    // Steps, participants and what the run belongs to are set by the execution, never taken over from a request.
    @Mappings({
            @Mapping(target = "steps", ignore = true),
            @Mapping(target = "participants", ignore = true),
            @Mapping(target = "experiment", ignore = true),
            @Mapping(target = "dataAnalysis", ignore = true)
    })
    ExecutionRunEntity dtoToEntity(ExecutionRunDTO dto);
}
