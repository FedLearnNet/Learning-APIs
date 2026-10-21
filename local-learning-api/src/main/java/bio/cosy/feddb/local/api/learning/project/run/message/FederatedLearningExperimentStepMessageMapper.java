package bio.cosy.feddb.local.api.learning.project.run.message;

import bio.cosy.feddb.core.execution.message.ExecutionMessageDTO;
import bio.cosy.feddb.core.base.BaseMapper;
import bio.cosy.feddb.local.helper.QuarkusMappingConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Mappings;

@Mapper(config = QuarkusMappingConfig.class)
public interface FederatedLearningExperimentStepMessageMapper extends BaseMapper<ExecutionMessageDTO, FederatedLearningExperimentStepMessageEntity> {

    @Mappings({
            @Mapping(target = "workerId", ignore = true),
            @Mapping(target = "runId", source = "step.id")
    })
    ExecutionMessageDTO entityToDto(FederatedLearningExperimentStepMessageEntity entity);

    @Mapping(target = "step.id", source = "runId")
    FederatedLearningExperimentStepMessageEntity dtoToEntity(ExecutionMessageDTO sourceCode);
}
