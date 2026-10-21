package de.unihamburg.daibetes.api.runs.test.federated.message;

import bio.cosy.feddb.core.execution.message.ExecutionMessageDTO;
import bio.cosy.feddb.core.base.BaseMapper;
import de.unihamburg.daibetes.helper.QuarkusMappingConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = QuarkusMappingConfig.class)
public interface FederatedParticipantMessageMapper extends BaseMapper<ExecutionMessageDTO, FederatedParticipantMessageEntity> {

    @Mapping(target = "runId", source = "participant.run.id")
    ExecutionMessageDTO entityToDto(FederatedParticipantMessageEntity entity);

    @Mapping(target = "participant.run.id", source = "runId")
    FederatedParticipantMessageEntity dtoToEntity(ExecutionMessageDTO dto);
}
