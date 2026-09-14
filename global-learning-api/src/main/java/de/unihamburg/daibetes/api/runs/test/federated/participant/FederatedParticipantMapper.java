package de.unihamburg.daibetes.api.runs.test.federated.participant;

import bio.cosy.feddb.core.base.BaseMapper;
import de.unihamburg.daibetes.helper.QuarkusMappingConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Mappings;

@Mapper(config = QuarkusMappingConfig.class)
public interface FederatedParticipantMapper extends BaseMapper<FederatedParticipantDTO, FederatedParticipantEntity> {

    @Mappings({
            @Mapping(target = "federatedRunId", source = "run.id"),
    })
    FederatedParticipantDTO entityToDto(FederatedParticipantEntity entity);

    @Mappings({
            @Mapping(target = "run.id", source = "federatedRunId"),
            @Mapping(target = "messages", ignore = true),
    })
    FederatedParticipantEntity dtoToEntity(FederatedParticipantDTO dto);

    @Mappings({
            @Mapping(target = "federatedRunId", source = "runId"),
            @Mapping(target = "participantId", source = "pCreate.participantId"),
            @Mapping(target = "role", source = "pCreate.role"),
            @Mapping(target = "status", constant = "PENDING"),
            @Mapping(target = "currentRound", constant = "0"),
            @Mapping(target = "messagesReceived", constant = "0"),
            @Mapping(target = "messagesSent", constant = "0"),
            @Mapping(target = "waitingFor", expression = "java(new java.util.ArrayList<>())"),
            @Mapping(target = "hyperParams", source = "pCreate.hyperParams"),
            @Mapping(target = "inputFilePaths", source = "pCreate.inputFilePaths"),
            @Mapping(target = "config", source = "pCreate.config"),
            @Mapping(target = "createdAt", ignore = true),
            @Mapping(target = "id", ignore = true),
            @Mapping(target = "updatedAt", ignore = true),
            @Mapping(target = "version", ignore = true)
    })
    FederatedParticipantDTO createDtoFromCreateDTO(FederatedParticipantCreateDTO pCreate, Long runId);
}
