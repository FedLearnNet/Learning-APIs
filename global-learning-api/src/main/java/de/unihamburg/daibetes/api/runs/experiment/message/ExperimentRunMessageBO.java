package de.unihamburg.daibetes.api.runs.experiment.message;

import bio.cosy.feddb.core.api.run.message.RunMessageDTO;
import bio.cosy.feddb.core.api.run.message.RunMessageLogDTO;
import bio.cosy.feddb.core.api.run.message.RunMessageMetricDTO;
import de.unihamburg.daibetes.api.app.author.FederatedAppAuthorBO;
import bio.cosy.feddb.core.api.run.message.RunMessageTypes;
import de.unihamburg.daibetes.api.runs.base.message.*;
import bio.cosy.feddb.core.base.BaseBo;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.ForbiddenException;

import java.util.List;

@ApplicationScoped
public class ExperimentRunMessageBO extends BaseBo<RunMessageDTO, ExperimentRunMessageEntity, ExperimentRunMessageAO, ExperimentRunMessageMapper> {

    @Inject
    FederatedAppAuthorBO authorBO;

    @Inject
    RunMessageLogMapper logMapper;

    @Inject
    RunMessageMetricMapper metricMapper;

    public List<RunMessageDTO> findByRun(Long appId, Long id, String keycloakId) {
        if (authorBO.isUserAuthor(keycloakId, appId)) {
            return mapper.entitiesToDtos(ao.findByRun(id));
        }
        throw new ForbiddenException();
    }

    public List<RunMessageLogDTO> findLogByRun(Long appId, Long id, String keycloakId) {
        if (authorBO.isUserAuthor(keycloakId, appId)) {
            List<RunMessageDTO> dtos = mapper.entitiesToDtos(ao.findByRun(id, RunMessageTypes.LOG));
            return logMapper.dtosToLogDtos(dtos);
        }
        throw new ForbiddenException();
    }

    public List<RunMessageMetricDTO> findMetricByRun(Long appId, Long id, String keycloakId) {
        if (authorBO.isUserAuthor(keycloakId, appId)) {
            List<RunMessageDTO> dtos = mapper.entitiesToDtos(ao.findByRun(id, RunMessageTypes.METRIC));
            return metricMapper.dtosToMetricDtos(dtos);
        }
        throw new ForbiddenException();
    }

    public List<RunMessageMetricDTO> findMetricByExperiment(Long appId, Long id, String keycloakId) {
        if (authorBO.isUserAuthor(keycloakId, appId)) {
            List<RunMessageDTO> dtos = mapper.entitiesToDtos(ao.findByExperiment(id, RunMessageTypes.METRIC));
            return metricMapper.dtosToMetricDtos(dtos);
        }
        throw new ForbiddenException();
    }

    public RunMessageLogDTO create(RunMessageLogDTO dto, Long runId) {
        if (runId != null) {
            dto.setRunId(runId);
            RunMessageDTO toCreate = logMapper.logDtoToDto(dto);
            RunMessageDTO created = create(toCreate);
            return logMapper.dtoToLogDTO(created);
        }
        return dto;
    }

    public RunMessageMetricDTO create(RunMessageMetricDTO dto, Long runId) {
        if (runId != null) {
            dto.setRunId(runId);
            RunMessageDTO toCreate = metricMapper.logDtoToDto(dto);
            RunMessageDTO created = create(toCreate);
            return metricMapper.dtoToMetricDTO(created);
        }
        return dto;
    }

}
