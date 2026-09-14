package bio.cosy.feddb.local.api.learning.project.run.message;

import bio.cosy.feddb.core.api.run.message.RunMessageDTO;
import bio.cosy.feddb.core.base.BaseMapper;
import bio.cosy.feddb.local.helper.QuarkusMappingConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Mappings;

@Mapper(config = QuarkusMappingConfig.class)
public interface FederatedLearningExperimentStepMessageMapper extends BaseMapper<RunMessageDTO, FederatedLearningExperimentStepMessageEntity> {

    @Mappings({
            @Mapping(target = "workerId", ignore = true),
            @Mapping(target = "runId", source = "step.id")
    })
    RunMessageDTO entityToDto(FederatedLearningExperimentStepMessageEntity entity);

    @Mapping(target = "step.id", source = "runId")
    FederatedLearningExperimentStepMessageEntity dtoToEntity(RunMessageDTO sourceCode);
}
