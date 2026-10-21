package de.unihamburg.daibetes.api.runs.test.message;

import bio.cosy.feddb.core.execution.message.ExecutionMessageDTO;
import bio.cosy.feddb.core.base.BaseMapper;
import de.unihamburg.daibetes.helper.QuarkusMappingConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = QuarkusMappingConfig.class)
public interface TestRunMessageMapper extends BaseMapper<ExecutionMessageDTO, TestRunMessageEntity> {

    @Mapping(target = "runId", source = "run.id")
    ExecutionMessageDTO entityToDto(TestRunMessageEntity entity);

    @Mapping(target = "run.id", source = "runId")
    TestRunMessageEntity dtoToEntity(ExecutionMessageDTO sourceCode);
}
