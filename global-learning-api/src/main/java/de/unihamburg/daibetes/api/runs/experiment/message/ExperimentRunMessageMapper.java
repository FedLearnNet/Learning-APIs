package de.unihamburg.daibetes.api.runs.experiment.message;

import bio.cosy.feddb.core.api.run.message.RunMessageDTO;
import bio.cosy.feddb.core.base.BaseMapper;
import de.unihamburg.daibetes.helper.QuarkusMappingConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = QuarkusMappingConfig.class)
public interface ExperimentRunMessageMapper extends BaseMapper<RunMessageDTO, ExperimentRunMessageEntity> {

    @Mapping(target = "runId", source = "run.id")
    RunMessageDTO entityToDto(ExperimentRunMessageEntity entity);

    @Mapping(target = "run.id", source = "runId")
    ExperimentRunMessageEntity dtoToEntity(RunMessageDTO sourceCode);
}
