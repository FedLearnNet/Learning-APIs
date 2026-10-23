package de.unihamburg.daibetes.execution.step;

import bio.cosy.feddb.core.execution.step.BaseExecutionRunStepMapper;
import de.unihamburg.daibetes.execution.attempt.ExecutionAttemptMapper;
import de.unihamburg.daibetes.helper.QuarkusMappingConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Mappings;

@Mapper(config = QuarkusMappingConfig.class, uses = ExecutionAttemptMapper.class)
public interface ExecutionRunStepMapper extends BaseExecutionRunStepMapper<ExecutionRunStepDTO, ExecutionRunStepEntity> {

    @Mappings({
            @Mapping(target = "runId", source = "run.id"),
            @Mapping(target = "runPublicId", source = "run.publicId"),
            @Mapping(target = "workflowNodeId", source = "workflowNode.id"),
            @Mapping(target = "selectedAttemptId", source = "selectedAttempt.id")
    })
    ExecutionRunStepDTO entityToDto(ExecutionRunStepEntity entity);

    @Mappings({
            @Mapping(target = "run.id", source = "runId"),
            @Mapping(target = "workflowNode.id", source = "workflowNodeId"),
            @Mapping(target = "selectedAttempt", ignore = true),
            @Mapping(target = "attempts", ignore = true)
    })
    ExecutionRunStepEntity dtoToEntity(ExecutionRunStepDTO dto);
}
