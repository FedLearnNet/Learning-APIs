package de.unihamburg.daibetes.api.analysis.chat.hitl;

import bio.cosy.feddb.core.api.model.workflow.chat.HumanInTheLoopDTO;
import bio.cosy.feddb.core.base.BaseMapper;
import de.unihamburg.daibetes.helper.QuarkusMappingConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Mappings;


@Mapper(config = QuarkusMappingConfig.class)
public interface HumanInTheLoopRequestMapper extends BaseMapper<HumanInTheLoopRequestDTO, HumanInTheLoopRequestEntity> {

    @Mappings({
            @Mapping(target = "messageId", source = "message.id")
    })
    HumanInTheLoopRequestDTO entityToDto(HumanInTheLoopRequestEntity entity);

    @Mappings({
            @Mapping(target = "message.id", source = "messageId")
    })
    HumanInTheLoopRequestEntity dtoToEntity(HumanInTheLoopRequestDTO dto);

    @Mappings({
            @Mapping(target = "question", source = "request"),
            @Mapping(target = "timestamp", source = "createdAt"),
    })
    HumanInTheLoopDTO entityToHITLDto(HumanInTheLoopRequestEntity entity);

}
