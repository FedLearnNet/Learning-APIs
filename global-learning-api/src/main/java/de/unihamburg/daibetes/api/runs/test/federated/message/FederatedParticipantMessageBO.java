package de.unihamburg.daibetes.api.runs.test.federated.message;

import bio.cosy.feddb.core.api.run.message.RunMessageDTO;
import bio.cosy.feddb.core.api.run.message.RunMessageLogDTO;
import bio.cosy.feddb.core.api.run.message.RunMessageMetricDTO;
import bio.cosy.feddb.core.api.run.message.RunMessageTypes;
import bio.cosy.feddb.core.base.BaseBo;
import de.unihamburg.daibetes.api.app.author.FederatedAppAuthorBO;
import de.unihamburg.daibetes.api.runs.base.message.RunMessageLogMapper;
import de.unihamburg.daibetes.api.runs.base.message.RunMessageMetricMapper;
import de.unihamburg.daibetes.api.runs.test.federated.participant.FederatedParticipantAO;
import de.unihamburg.daibetes.api.runs.test.federated.participant.FederatedParticipantEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotFoundException;

import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class FederatedParticipantMessageBO extends BaseBo<RunMessageDTO, FederatedParticipantMessageEntity, FederatedParticipantMessageAO, FederatedParticipantMessageMapper> {

    @Inject
    FederatedParticipantAO participantAO;

    @Inject
    RunMessageLogMapper logMapper;

    @Inject
    RunMessageMetricMapper metricMapper;

    @Inject
    FederatedAppAuthorBO authorBO;

    public List<RunMessageDTO> findByParticipant(Long appId, Long participantId, String keycloakId) {
        if (authorBO.isUserAuthor(keycloakId, appId)) {
            return mapper.entitiesToDtos(ao.findByParticipant(participantId));
        }
        throw new ForbiddenException();
    }

    public List<RunMessageLogDTO> findLogByParticipant(Long appId, Long participantId, String keycloakId) {
        if (authorBO.isUserAuthor(keycloakId, appId)) {
            List<RunMessageDTO> dtos = mapper.entitiesToDtos(ao.findByParticipant(participantId, RunMessageTypes.LOG));
            return logMapper.dtosToLogDtos(dtos);
        }
        throw new ForbiddenException();
    }

    public List<RunMessageMetricDTO> findMetricByParticipant(Long appId, Long participantId, String keycloakId) {
        if (authorBO.isUserAuthor(keycloakId, appId)) {
            List<RunMessageDTO> dtos = mapper.entitiesToDtos(ao.findByParticipant(participantId, RunMessageTypes.METRIC));
            return metricMapper.dtosToMetricDtos(dtos);
        }
        throw new ForbiddenException();
    }

    public RunMessageLogDTO create(RunMessageLogDTO dto, Long runId, String participantId) {
        Optional<FederatedParticipantEntity> participant = participantAO.findByRunAndParticipant(runId, participantId);
        if (participant.isEmpty()) {
            throw new NotFoundException("Participant not found: " + participantId);
        }
        RunMessageDTO raw = logMapper.logDtoToDto(dto);
        FederatedParticipantMessageEntity entity = mapper.dtoToEntity(raw);
        entity.setParticipant(participant.get());
        ao.persist(entity);
        return logMapper.dtoToLogDTO(mapper.entityToDto(entity));
    }

    public RunMessageMetricDTO create(RunMessageMetricDTO dto, Long runId, String participantId) {
        Optional<FederatedParticipantEntity> participant = participantAO.findByRunAndParticipant(runId, participantId);
        if (participant.isEmpty()) {
            throw new NotFoundException("Participant not found: " + participantId);
        }
        RunMessageDTO raw = metricMapper.logDtoToDto(dto);
        FederatedParticipantMessageEntity entity = mapper.dtoToEntity(raw);
        entity.setParticipant(participant.get());
        ao.persist(entity);
        return metricMapper.dtoToMetricDTO(mapper.entityToDto(entity));
    }
}
