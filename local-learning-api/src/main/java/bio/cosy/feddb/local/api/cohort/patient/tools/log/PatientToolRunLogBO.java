package bio.cosy.feddb.local.api.cohort.patient.tools.log;

import bio.cosy.feddb.core.execution.message.ExecutionMessageDTO;
import bio.cosy.feddb.core.execution.message.ExecutionMessageLogDTO;
import bio.cosy.feddb.core.execution.message.ExecutionMessageMetricDTO;
import bio.cosy.feddb.core.base.BaseBo;
import bio.cosy.feddb.local.api.cohort.patient.tools.PatientToolRunEventSender;
import bio.cosy.feddb.local.api.learning.project.run.message.RunMessageLogMapper;
import bio.cosy.feddb.local.api.learning.project.run.message.RunMessageMetricMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;

@ApplicationScoped
public class PatientToolRunLogBO extends BaseBo<ExecutionMessageDTO, PatientToolRunLogEntity, PatientToolRunLogAO, PatientToolRunLogMapper> {

    @Inject
    RunMessageLogMapper logMapper;

    @Inject
    RunMessageMetricMapper metricMapper;

    @Inject
    PatientToolRunEventSender eventSender;

    public void deleteAllForRuns(List<Long> runIds) {
        ao.deleteByRunIds(runIds);
    }

    public List<ExecutionMessageDTO> findByRunAfter(Long runId, Long afterId) {
        return mapper.entitiesToDtos(ao.findByRunIdAfter(runId, afterId));
    }


    public ExecutionMessageLogDTO create(ExecutionMessageLogDTO dto, Long runId) {
        if (runId != null) {
            dto.setRunId(runId);
            ExecutionMessageDTO toCreate = logMapper.logDtoToDto(dto);
            ExecutionMessageDTO created = create(toCreate);
            ExecutionMessageLogDTO createdDto = logMapper.dtoToLogDTO(created);
            sendInfo(createdDto);
            return createdDto;
        }
        sendInfo(dto);
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

    /** Hands a stored log line to the progress streams of its run. */
    protected void sendInfo(ExecutionMessageLogDTO dto) {
        eventSender.sendLog(dto.getRunId(), dto);
    }

}
