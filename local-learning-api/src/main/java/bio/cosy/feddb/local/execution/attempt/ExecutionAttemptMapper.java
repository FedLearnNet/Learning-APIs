package bio.cosy.feddb.local.execution.attempt;

import bio.cosy.feddb.core.execution.attempt.BaseExecutionAttemptMapper;
import bio.cosy.feddb.local.helper.QuarkusMappingConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Mappings;

@Mapper(config = QuarkusMappingConfig.class)
public interface ExecutionAttemptMapper extends BaseExecutionAttemptMapper<ExecutionAttemptDTO, ExecutionAttemptEntity> {

    @Mappings({
            @Mapping(target = "stepId", source = "step.id"),
            @Mapping(target = "stepPublicId", source = "step.publicId"),
            @Mapping(target = "runId", source = "step.run.id"),
            @Mapping(target = "runPublicId", source = "step.run.publicId"),
            @Mapping(target = "scoreMetric", source = "scoreMessage"),
            @Mapping(target = "outputData", ignore = true),
            @Mapping(target = "lastLog", ignore = true),
            @Mapping(target = "logs", ignore = true),
            @Mapping(target = "metrics", ignore = true),
            @Mapping(target = "inputFiles", ignore = true),
            @Mapping(target = "outputFiles", ignore = true)
    })
    ExecutionAttemptDTO entityToDto(ExecutionAttemptEntity entity);

    @Mappings({
            @Mapping(target = "step.id", source = "stepId"),
            @Mapping(target = "scoreMessage", ignore = true)
    })
    ExecutionAttemptEntity dtoToEntity(ExecutionAttemptDTO dto);
}
