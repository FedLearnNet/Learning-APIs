package de.unihamburg.daibetes.api.analysis.worklfow.message;

import bio.cosy.feddb.core.api.run.message.RunMessageDTO;
import bio.cosy.feddb.core.api.run.message.RunMessageLogDTO;
import bio.cosy.feddb.core.base.BaseMapper;
import de.unihamburg.daibetes.api.file.FileMapper;
import de.unihamburg.daibetes.api.runs.base.message.RunMessageLogMapper;
import de.unihamburg.daibetes.helper.QuarkusMappingConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Mappings;
import org.mapstruct.factory.Mappers;

@Mapper(config = QuarkusMappingConfig.class)
public interface DataAnalysisWorkflowRunMessagesMapper extends BaseMapper<DataAnalysisWorkflowRunMessagesDTO, DataAnalysisWorkflowRunMessagesEntity> {

    FileMapper fileMapper = Mappers.getMapper(FileMapper.class);

    @Mappings({
            @Mapping(target = "predictionId", source = "prediction.id"),
            @Mapping(target = "runId", source = "step.id")
    })
    DataAnalysisWorkflowRunMessagesDTO entityToDto(DataAnalysisWorkflowRunMessagesEntity entity);

    @Mappings({
            @Mapping(target = "runId", ignore = true),
            @Mapping(target = "predictionId", ignore = true),
            @Mapping(target = "runMode", ignore = true)
    })
    DataAnalysisWorkflowRunMessagesDTO runDtoToDto(RunMessageDTO sourceCode);

    @Mapping(target = "prediction.id", source = "predictionId")
    @Mapping(target = "step.id", source = "runId")
    DataAnalysisWorkflowRunMessagesEntity dtoToEntity(DataAnalysisWorkflowRunMessagesDTO sourceCode);
}
