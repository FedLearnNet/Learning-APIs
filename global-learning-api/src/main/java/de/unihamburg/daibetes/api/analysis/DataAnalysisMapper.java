package de.unihamburg.daibetes.api.analysis;

import bio.cosy.feddb.core.api.model.workflow.DataAnalysisDetailDTO;
import bio.cosy.feddb.core.api.model.workflow.ModelWorkflowDTO;
import bio.cosy.feddb.core.api.model.workflow.chat.DataAnalysisChatWrapperDTO;
import bio.cosy.feddb.core.api.model.workflow.chat.ModelWorkflowChatMessageDTO;
import bio.cosy.feddb.core.api.model.workflow.chat.ModelWorkflowChatMessageType;
import bio.cosy.feddb.core.base.BaseMapper;
import de.unihamburg.daibetes.api.analysis.chat.DataAnalysisChatMessageMapper;
import de.unihamburg.daibetes.api.analysis.file.DataAnalysisFileMapper;
import de.unihamburg.daibetes.helper.QuarkusMappingConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Mappings;
import org.mapstruct.factory.Mappers;

import java.util.List;
import java.util.Optional;

@Mapper(config = QuarkusMappingConfig.class)
public interface DataAnalysisMapper extends BaseMapper<ModelWorkflowDTO, DataAnalysisEntity> {

    DataAnalysisFileMapper modelWorkflowFileMapper = Mappers.getMapper(DataAnalysisFileMapper.class);
    DataAnalysisChatMessageMapper modelWorkflowChatMessageMapper = Mappers.getMapper(DataAnalysisChatMessageMapper.class);

    @Mappings({
    })
    ModelWorkflowDTO entityToDto(DataAnalysisEntity entity);

    @Mappings({
            @Mapping(target = "files", ignore = true),
            @Mapping(target = "chatMessages", ignore = true),
            @Mapping(target = "runs", ignore = true)
    })
    DataAnalysisEntity dtoToEntity(ModelWorkflowDTO dto);

    @Mappings({
            @Mapping(target = "files", expression = "java(modelWorkflowFileMapper.entitiesToDtos(entity.getFiles()))"),
            @Mapping(target = "messages", expression = "java(mapMessages(entity))"),
    })
    DataAnalysisDetailDTO entityToDetail(DataAnalysisEntity entity);

    @Mappings({
            @Mapping(target = "files", expression = "java(modelWorkflowFileMapper.entitiesToDtos(entity.getFiles()))"),
            @Mapping(target = "messages", expression = "java(mapMessages(entity))"),
    })
    DataAnalysisDetailDTO entityToDetailFlat(DataAnalysisEntity entity);

    default List<DataAnalysisChatWrapperDTO> mapMessages(DataAnalysisEntity entity) {
        List<ModelWorkflowChatMessageDTO> messages = modelWorkflowChatMessageMapper.entitiesToDtos(entity.getChatMessages());
        return Optional.ofNullable(messages).stream().flatMap(List::stream)
                .map(message -> {
                    DataAnalysisChatWrapperDTO<ModelWorkflowChatMessageDTO> wrapper = new DataAnalysisChatWrapperDTO<>();
                    wrapper.setMessage(message);
                    wrapper.setType(ModelWorkflowChatMessageType.CHAT_MESSAGE);
                    return (DataAnalysisChatWrapperDTO) wrapper;
                })
                .toList();
    }
}
