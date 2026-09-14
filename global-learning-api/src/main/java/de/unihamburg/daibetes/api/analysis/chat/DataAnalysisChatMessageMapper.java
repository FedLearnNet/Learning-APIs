package de.unihamburg.daibetes.api.analysis.chat;

import bio.cosy.feddb.core.api.model.workflow.chat.HumanInTheLoopDTO;
import bio.cosy.feddb.core.api.model.workflow.chat.ModelWorkflowChatMessageDTO;
import bio.cosy.feddb.core.base.BaseMapper;
import de.unihamburg.daibetes.api.analysis.chat.hitl.HumanInTheLoopRequestEntity;
import de.unihamburg.daibetes.api.analysis.chat.hitl.HumanInTheLoopRequestMapper;
import de.unihamburg.daibetes.helper.QuarkusMappingConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Mappings;
import org.mapstruct.Named;
import org.mapstruct.factory.Mappers;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;


@Mapper(config = QuarkusMappingConfig.class)
public interface DataAnalysisChatMessageMapper extends BaseMapper<ModelWorkflowChatMessageDTO, DataAnalysisChatMessageEntity> {

    HumanInTheLoopRequestMapper hitlMapper = Mappers.getMapper(HumanInTheLoopRequestMapper.class);

    @Mappings({
            @Mapping(target = "workflowId", source = "dataAnalysis.id"),
            @Mapping(target = "humanInTheLoop", source = "entity.humanInTheLoopRequests", qualifiedByName = "mapHITL")
    })
    ModelWorkflowChatMessageDTO entityToDto(DataAnalysisChatMessageEntity entity);

    @Mappings({
            @Mapping(target = "dataAnalysis.id", source = "workflowId"),
            @Mapping(target = "humanInTheLoopRequests", ignore = true)
    })
    DataAnalysisChatMessageEntity dtoToEntity(ModelWorkflowChatMessageDTO dto);

    @Named("mapHITL")
    default List<HumanInTheLoopDTO> mapHITL(Set<HumanInTheLoopRequestEntity> humanInTheLoopRequests) {
        if(humanInTheLoopRequests == null) {
            return new ArrayList<>();
        }
        return humanInTheLoopRequests.stream()
                .map(hitlMapper::entityToHITLDto)
                .toList();
    }

}
