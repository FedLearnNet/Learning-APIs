package de.unihamburg.daibetes.api.project.experiment.local.message;

import bio.cosy.feddb.core.execution.message.ExecutionMessageDTO;
import bio.cosy.feddb.core.base.BaseMapper;
import de.unihamburg.daibetes.helper.QuarkusMappingConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = QuarkusMappingConfig.class)
public interface ProjectLocalExperimentStepMessageMapper extends BaseMapper<ExecutionMessageDTO, ProjectLocalExperimentStepMessageEntity> {

    @Mapping(target = "runId", source = "step.id")
    ExecutionMessageDTO entityToDto(ProjectLocalExperimentStepMessageEntity entity);

    @Mapping(target = "step.id", source = "runId")
    ProjectLocalExperimentStepMessageEntity dtoToEntity(ExecutionMessageDTO sourceCode);
}
