package bio.cosy.feddb.local.api.learning.project.run.message;

import bio.cosy.feddb.core.api.run.message.RunMessageDTO;
import bio.cosy.feddb.core.api.run.message.RunMessageLogDTO;
import bio.cosy.feddb.core.api.run.message.RunMessageMetricDTO;
import bio.cosy.feddb.core.api.run.message.RunMessageTypes;
import bio.cosy.feddb.core.base.BaseBo;
import bio.cosy.feddb.local.api.cohort.member.CohortMemberAuthBO;
import bio.cosy.feddb.local.api.search.SearchResultDTO;
import bio.cosy.feddb.local.api.search.SearchResultType;
import bio.cosy.feddb.local.api.search.SearchScoreUtil;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.OnOverflow;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static bio.cosy.feddb.local.api.learning.project.FederatedLearningProjectServiceImpl.TRAINING_STEP_LOG_CHANNEL;

@ApplicationScoped
public class FederatedLearningExperimentStepMessageBO extends BaseBo<RunMessageDTO, FederatedLearningExperimentStepMessageEntity, FederatedLearningExperimentStepMessageAO, FederatedLearningExperimentStepMessageMapper> {

    @Inject
    RunMessageLogMapper logMapper;

    @Inject
    CohortMemberAuthBO cohortMemberAuthBO;

    @Inject
    RunMessageMetricMapper metricMapper;

    @Inject
    @Channel(TRAINING_STEP_LOG_CHANNEL)
    @OnOverflow(OnOverflow.Strategy.DROP)
    Emitter<RunMessageLogDTO> infoEmitter;

    public List<RunMessageDTO> findByRun(Long projectId, String keycloakId) {
        return mapper.entitiesToDtos(ao.findByProject(projectId));
    }

    public List<SearchResultDTO<RunMessageDTO>> search(String query, String keycloakId) {
        return ao.listAll().stream()
                .filter(message -> message.getType() == RunMessageTypes.LOG)
                .filter(message -> isAllowed(message, keycloakId))
                .map(message -> {
                    RunMessageDTO dto = mapper.entityToDto(message);
                    String title = dto.getMessage();
                    int score = SearchScoreUtil.score(query, title, dto.getProcess(), dto.getWorkerId());
                    return SearchScoreUtil.toResult(SearchResultType.LOG, dto, title, score);
                })
                .filter(result -> result.getScore() > 0)
                .toList();
    }

    public List<RunMessageLogDTO> findLogByStepId(Long stepId) {
        List<RunMessageDTO> dtos = mapper.entitiesToDtos(ao.findByStep(stepId, RunMessageTypes.LOG));
        return logMapper.dtosToLogDtos(dtos);
    }

    public List<RunMessageMetricDTO> findMetricByStepId(Long stepId) {
        List<RunMessageDTO> dtos = mapper.entitiesToDtos(ao.findByStep(stepId, RunMessageTypes.METRIC));
        return metricMapper.dtosToMetricDtos(dtos);
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

    protected void sendInfo(RunMessageLogDTO dto) {
        try {
            infoEmitter.send(dto);
            Log.debug("SSE event sent: " + dto);
        } catch (Exception e) {
            Log.debug("Attempted to send SSE event, but client connection was already closed.", e);
        }
    }

    private boolean isAllowed(FederatedLearningExperimentStepMessageEntity message, String keycloakId) {
        if (message.getStep() == null || message.getStep().getExperiment() == null ||
                message.getStep().getExperiment().getProject() == null) {
            return false;
        }
        var project = message.getStep().getExperiment().getProject();
        if (project.getRequest() != null && project.getRequest().getPatients() != null) {
            Set<Long> cohortIds = project.getRequest().getPatients().stream()
                    .map(patientLearning -> patientLearning.getPatient().getCohort().getId())
                    .collect(Collectors.toSet());
            return hasCohortSearchAccess(cohortIds, keycloakId);
        }
        if (project.getQuery() != null && project.getQuery().getPatients() != null) {
            Set<Long> cohortIds = project.getQuery().getPatients().stream()
                    .map(queryPatient -> queryPatient.getPatient().getCohort().getId())
                    .collect(Collectors.toSet());
            return hasCohortSearchAccess(cohortIds, keycloakId);
        }
        return false;
    }

    private boolean hasCohortSearchAccess(Set<Long> cohortIds, String keycloakId) {
        return cohortIds != null && !cohortIds.isEmpty() && cohortIds.stream()
                .allMatch(cohortId -> cohortMemberAuthBO.isMember(cohortId, keycloakId));
    }

}
