package de.unihamburg.daibetes.api.runs.test.federated.message;

import bio.cosy.feddb.core.api.run.message.RunMessageDTO;
import bio.cosy.feddb.core.base.BaseMapper;
import de.unihamburg.daibetes.helper.QuarkusMappingConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = QuarkusMappingConfig.class)
public interface FederatedParticipantMessageMapper extends BaseMapper<RunMessageDTO, FederatedParticipantMessageEntity> {

    @Mapping(target = "runId", source = "participant.run.id")
    RunMessageDTO entityToDto(FederatedParticipantMessageEntity entity);

    @Mapping(target = "participant.run.id", source = "runId")
    FederatedParticipantMessageEntity dtoToEntity(RunMessageDTO dto);
}
