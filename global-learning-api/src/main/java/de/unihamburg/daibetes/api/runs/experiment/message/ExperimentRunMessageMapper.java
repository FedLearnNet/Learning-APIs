package de.unihamburg.daibetes.api.runs.experiment.message;

import bio.cosy.feddb.core.execution.message.ExecutionMessageDTO;
import bio.cosy.feddb.core.base.BaseMapper;
import de.unihamburg.daibetes.helper.QuarkusMappingConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = QuarkusMappingConfig.class)
public interface ExperimentRunMessageMapper extends BaseMapper<ExecutionMessageDTO, ExperimentRunMessageEntity> {

    @Mapping(target = "runId", source = "run.id")
    ExecutionMessageDTO entityToDto(ExperimentRunMessageEntity entity);

    @Mapping(target = "run.id", source = "runId")
    ExperimentRunMessageEntity dtoToEntity(ExecutionMessageDTO sourceCode);
}
