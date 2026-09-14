package bio.cosy.feddb.local.api.cohort.patient.tools.log;

import bio.cosy.feddb.core.api.run.message.RunMessageDTO;
import bio.cosy.feddb.core.api.run.message.RunMessageLogDTO;
import bio.cosy.feddb.core.api.run.message.RunMessageMetricDTO;
import bio.cosy.feddb.core.base.BaseBo;
import bio.cosy.feddb.local.api.cohort.patient.tools.PatientToolRunEventSender;
import bio.cosy.feddb.local.api.learning.project.run.message.RunMessageLogMapper;
import bio.cosy.feddb.local.api.learning.project.run.message.RunMessageMetricMapper;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;

@ApplicationScoped
public class PatientToolRunLogBO extends BaseBo<RunMessageDTO, PatientToolRunLogEntity, PatientToolRunLogAO, PatientToolRunLogMapper> {

    @Inject
    RunMessageLogMapper logMapper;

    @Inject
    RunMessageMetricMapper metricMapper;

    @Inject
    PatientToolRunEventSender eventSender;

    public void deleteAllForRuns(List<Long> runIds) {
        ao.deleteByRunIds(runIds);
    }

    public List<RunMessageDTO> findByRunAfter(Long runId, Long afterId) {
        return mapper.entitiesToDtos(ao.findByRunIdAfter(runId, afterId));
    }


    public RunMessageLogDTO create(RunMessageLogDTO dto, Long runId) {
        if (runId != null) {
            dto.setRunId(runId);
            RunMessageDTO toCreate = logMapper.logDtoToDto(dto);
            RunMessageDTO created = create(toCreate);
            RunMessageLogDTO createdDto = logMapper.dtoToLogDTO(created);
            sendInfo(createdDto);
            return createdDto;
        }
        sendInfo(dto);
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

    /** Hands a stored log line to the progress streams of its run. */
    protected void sendInfo(RunMessageLogDTO dto) {
        eventSender.sendLog(dto.getRunId(), dto);
    }

}
