package de.unihamburg.daibetes.api.runs.test.message;

import bio.cosy.feddb.core.api.run.message.RunMessageDTO;
import bio.cosy.feddb.core.base.BaseMapper;
import de.unihamburg.daibetes.helper.QuarkusMappingConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = QuarkusMappingConfig.class)
public interface TestRunMessageMapper extends BaseMapper<RunMessageDTO, TestRunMessageEntity> {

    @Mapping(target = "runId", source = "run.id")
    RunMessageDTO entityToDto(TestRunMessageEntity entity);

    @Mapping(target = "run.id", source = "runId")
    TestRunMessageEntity dtoToEntity(RunMessageDTO sourceCode);
}
