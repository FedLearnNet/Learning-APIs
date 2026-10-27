package de.unihamburg.daibetes.execution.federation;

import bio.cosy.feddb.core.execution.message.ExecutionMessageRoundDTO;
import bio.cosy.feddb.core.execution.run.dto.FederatedRunParticipantDTO;
import bio.cosy.feddb.core.execution.run.dto.StartRunDTO;
import de.unihamburg.daibetes.api.development.run.federated.FederatedParticipantCreateDTO;
import de.unihamburg.daibetes.api.development.run.federated.FederatedRoundMessageCreateDTO;
import de.unihamburg.daibetes.api.development.run.federated.FederatedTestRunCreateDTO;
import de.unihamburg.daibetes.execution.attempt.ExecutionAttemptEntity;
import de.unihamburg.daibetes.execution.step.ExecutionRunStepEntity;
import de.unihamburg.daibetes.helper.QuarkusMappingConfig;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Mappings;

@Mapper(config = QuarkusMappingConfig.class)
public interface ExecutionFederationMapper {

    @Mappings({
            @Mapping(target = "pollInterval", source = "config.pollInterval"),
            @Mapping(target = "timeout", source = "config.timeout"),
            @Mapping(target = "maxPolls", source = "config.maxPolls")
    })
    ExecutionFederationDTO entityToDto(ExecutionFederationEntity entity);

    @Mapping(target = "runId", source = "run.id")
    ExecutionParticipantDTO participantToDto(ExecutionParticipantEntity entity);

    @BeanMapping(ignoreByDefault = true)
    @Mappings({
            @Mapping(target = "totalRounds", source = "totalRounds"),
            @Mapping(target = "startAggregator", source = "startAggregator"),
            @Mapping(target = "config", source = "config", defaultExpression = "java(new bio.cosy.feddb.core.execution.run.dto.FederatedRunConfigDTO())")
    })
    ExecutionFederationEntity createDtoToEntity(FederatedTestRunCreateDTO create);

    @BeanMapping(ignoreByDefault = true)
    @Mappings({
            @Mapping(target = "participantId", source = "participantId"),
            @Mapping(target = "role", source = "role"),
            @Mapping(target = "inputFilePaths", source = "inputFilePaths")
    })
    ExecutionParticipantEntity createDtoToEntity(FederatedParticipantCreateDTO create);

    // A clinic that accepted a training.
    @BeanMapping(ignoreByDefault = true)
    @Mappings({
            @Mapping(target = "participantId", source = "clinicId"),
            @Mapping(target = "modelCanBePublic", source = "modelCanBePublic"),
            @Mapping(target = "role", constant = "CLIENT"),
            @Mapping(target = "status", constant = "INITIALIZED")
    })
    ExecutionParticipantEntity clinicToParticipantEntity(String clinicId, Boolean modelCanBePublic);

    @Mappings({
            @Mapping(target = "participantId", source = "federation.participant.participantId"),
            @Mapping(target = "role", source = "federation.participant.role"),
            @Mapping(target = "hyperParams", source = "params"),
            @Mapping(target = "inputFilePaths", source = "federation.participant.inputFilePaths")
    })
    FederatedRunParticipantDTO attemptToParticipantDTO(ExecutionAttemptEntity attempt);

    // What the app engine runs for a federated test run: every participant, addressed by the run.
    @BeanMapping(ignoreByDefault = true)
    @Mappings({
            @Mapping(target = "id", source = "run.id"),
            @Mapping(target = "status", constant = "PENDING"),
            @Mapping(target = "supportFederatedLearning", constant = "true"),
            @Mapping(target = "totalRounds", source = "federation.totalRounds"),
            @Mapping(target = "startAggregator", source = "federation.startAggregator"),
            @Mapping(target = "config", source = "federation.config"),
            @Mapping(target = "participants", source = "attempts"),
            @Mapping(target = "federatedAppId", source = "federatedAppVersion.federatedApp.id"),
            @Mapping(target = "federatedAppVersionId", source = "federatedAppVersion.id")
    })
    StartRunDTO stepToFederatedStartRunDTO(ExecutionRunStepEntity step);

    @BeanMapping(ignoreByDefault = true)
    @Mappings({
            @Mapping(target = "direction", source = "direction"),
            @Mapping(target = "round", source = "round"),
            @Mapping(target = "fromParticipant", source = "fromParticipant"),
            @Mapping(target = "toParticipant", source = "toParticipant"),
            @Mapping(target = "communicationId", source = "communicationId"),
            @Mapping(target = "payloadPreview", source = "payloadPreview"),
            @Mapping(target = "workerId", source = "reportingParticipantId")
    })
    ExecutionMessageRoundDTO createDtoToRoundDTO(FederatedRoundMessageCreateDTO create);
}
