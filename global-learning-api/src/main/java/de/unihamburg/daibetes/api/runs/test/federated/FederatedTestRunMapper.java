package de.unihamburg.daibetes.api.runs.test.federated;

import bio.cosy.feddb.core.base.BaseMapper;
import de.unihamburg.daibetes.api.runs.test.federated.message.FederatedRoundMessageMapper;
import de.unihamburg.daibetes.api.runs.test.federated.participant.FederatedParticipantMapper;
import de.unihamburg.daibetes.helper.QuarkusMappingConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Mappings;
import org.mapstruct.factory.Mappers;

@Mapper(config = QuarkusMappingConfig.class)
public interface FederatedTestRunMapper extends BaseMapper<FederatedTestRunDTO, FederatedTestRunEntity> {

    FederatedParticipantMapper participantMapper = Mappers.getMapper(FederatedParticipantMapper.class);
    FederatedRoundMessageMapper roundMessageMapper = Mappers.getMapper(FederatedRoundMessageMapper.class);

    @Mappings({
            @Mapping(target = "federatedAppId", source = "federatedAppVersion.federatedApp.id"),
            @Mapping(target = "federatedAppVersionId", source = "federatedAppVersion.id"),
            @Mapping(target = "federatedAppVersionName", source = "federatedAppVersion.versionFormatted"),
            @Mapping(target = "participants", expression = "java(entity.getParticipants() != null ? participantMapper.entitiesToDtos(entity.getParticipants()) : null)"),
            @Mapping(target = "roundMessages", expression = "java(entity.getRoundMessages() != null ? roundMessageMapper.entitiesToDtos(entity.getRoundMessages()) : null)"),
    })
    FederatedTestRunDTO entityToDto(FederatedTestRunEntity entity);

    @Mappings({
            @Mapping(target = "federatedAppVersion.id", source = "federatedAppVersionId"),
            @Mapping(target = "participants", ignore = true),
            @Mapping(target = "roundMessages", ignore = true),
    })
    FederatedTestRunEntity dtoToEntity(FederatedTestRunDTO dto);

    @Mappings({
            @Mapping(target = "id", ignore = true),
            @Mapping(target = "createdAt", ignore = true),
            @Mapping(target = "updatedAt", ignore = true),
            @Mapping(target = "version", ignore = true),
            @Mapping(target = "federatedAppId", source = "appId"),
            @Mapping(target = "federatedAppVersionId", source = "createDTO.federatedAppVersionId"),
            @Mapping(target = "status", constant = "PENDING"),
            @Mapping(target = "currentRound", constant = "0"),
            @Mapping(target = "totalRounds", source = "createDTO.totalRounds"),
            @Mapping(target = "startAggregator", source = "createDTO.startAggregator"),
            @Mapping(target = "config", expression = "java(createDTO.getConfig() == null ? new FederatedTestRunConfigDTO() : createDTO.getConfig())"),
            @Mapping(target = "error", ignore = true),
            @Mapping(target = "outputData", ignore = true),
            @Mapping(target = "aggregatorId", ignore = true),
            @Mapping(target = "federatedAppVersionName", ignore = true),
            @Mapping(target = "participants", ignore = true),
            @Mapping(target = "roundMessages", ignore = true),
    })
    FederatedTestRunDTO createDtoFromRequest(Long appId, FederatedTestRunCreateDTO createDTO);
}
