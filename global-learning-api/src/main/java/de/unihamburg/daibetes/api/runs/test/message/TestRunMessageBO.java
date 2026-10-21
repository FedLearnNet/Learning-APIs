package de.unihamburg.daibetes.api.runs.test.message;

import bio.cosy.feddb.core.execution.message.*;
import de.unihamburg.daibetes.api.app.author.FederatedAppAuthorBO;
import de.unihamburg.daibetes.api.runs.base.message.*;
import bio.cosy.feddb.core.base.BaseBo;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.ForbiddenException;

import java.util.List;

@ApplicationScoped
public class TestRunMessageBO extends BaseBo<ExecutionMessageDTO, TestRunMessageEntity, TestRunMessageAO, TestRunMessageMapper> {

    @Inject
    FederatedAppAuthorBO authorBO;

    @Inject
    RunMessageLogMapper logMapper;

    @Inject
    RunMessageMetricMapper metricMapper;

    public List<ExecutionMessageDTO> findByRun(Long appId, Long id, String keycloakId) {
        if (authorBO.isUserAuthor(keycloakId, appId)) {
            return mapper.entitiesToDtos(ao.findByRun(id));
        }
        throw new ForbiddenException();
    }

    public List<ExecutionMessageLogDTO> findLogByRun(Long appId, Long id, String keycloakId) {
        if (authorBO.isUserAuthor(keycloakId, appId)) {
            List<ExecutionMessageDTO> dtos = mapper.entitiesToDtos(ao.findByRun(id, RunMessageTypes.LOG));
            return logMapper.dtosToLogDtos(dtos);
        }
        throw new ForbiddenException();
    }

    public List<ExecutionMessageMetricDTO> findMetricByRun(Long appId, Long id, String keycloakId) {
        if (authorBO.isUserAuthor(keycloakId, appId)) {
            List<ExecutionMessageDTO> dtos = mapper.entitiesToDtos(ao.findByRun(id, RunMessageTypes.METRIC));
            return metricMapper.dtosToMetricDtos(dtos);
        }
        throw new ForbiddenException();
    }

    public ExecutionMessageLogDTO create(ExecutionMessageLogDTO dto, Long runId) {
        if (runId != null) {
            dto.setRunId(runId);
            ExecutionMessageDTO toCreate = logMapper.logDtoToDto(dto);
            ExecutionMessageDTO created = create(toCreate);
            return logMapper.dtoToLogDTO(created);
        }
        return dto;
    }

    public ExecutionMessageMetricDTO create(ExecutionMessageMetricDTO dto, Long runId) {
        if (runId != null) {
            dto.setRunId(runId);
            ExecutionMessageDTO toCreate = metricMapper.logDtoToDto(dto);
            ExecutionMessageDTO created = create(toCreate);
            return metricMapper.dtoToMetricDTO(created);
        }
        return dto;
    }

}
