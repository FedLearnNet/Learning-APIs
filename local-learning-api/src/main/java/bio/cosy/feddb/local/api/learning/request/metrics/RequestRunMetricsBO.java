package bio.cosy.feddb.local.api.learning.request.metrics;

import bio.cosy.feddb.core.api.run.message.RunMessageMetricDTO;
import bio.cosy.feddb.core.api.run.message.RunMessageTypes;
import bio.cosy.feddb.core.api.socket.ProjectFederatedRequestRunMetricsDTO;
import bio.cosy.feddb.core.api.socket.RunMetricDTO;
import bio.cosy.feddb.core.api.socket.RunMetricsResponseClientDTO;
import bio.cosy.feddb.core.base.BaseBo;
import bio.cosy.feddb.core.base.PagedResponse;
import bio.cosy.feddb.local.api.cohort.member.CohortMemberAuthBO;
import bio.cosy.feddb.local.api.cohort.permission.PermissionBO;
import bio.cosy.feddb.local.api.eam.WebsocketSender;
import bio.cosy.feddb.local.api.learning.project.FederatedLearningProjectAO;
import bio.cosy.feddb.local.api.learning.project.FederatedLearningProjectEntity;
import bio.cosy.feddb.local.api.learning.project.run.FederatedLearningExperimentEntity;
import bio.cosy.feddb.local.api.learning.project.run.message.FederatedLearningExperimentStepMessageAO;
import bio.cosy.feddb.local.api.learning.project.run.message.FederatedLearningExperimentStepMessageMapper;
import bio.cosy.feddb.local.api.learning.project.run.message.RunMessageMetricMapper;
import bio.cosy.feddb.local.api.learning.request.FederatedLearningRequestStatus;
import bio.cosy.feddb.local.api.notification.WebsocketClientNotificationBO;
import bio.cosy.feddb.local.api.search.SearchResultDTO;
import bio.cosy.feddb.local.api.search.SearchResultType;
import bio.cosy.feddb.local.api.search.SearchScoreUtil;
import bio.cosy.feddb.local.config.FLNetClientConfig;
import io.quarkus.logging.Log;
import io.quarkus.panache.common.Page;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;

import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@ApplicationScoped
public class RequestRunMetricsBO extends BaseBo<RequestRunMetricsDTO, RequestRunMetricsEntity, RequestRunMetricsAO, RequestRunMetricsMapper> {

    @Inject
    FederatedLearningProjectAO projectAO;

    @Inject
    CohortMemberAuthBO cohortMemberAuthBO;

    @Inject
    FederatedLearningExperimentStepMessageAO stepMessageAO;

    @Inject
    FederatedLearningExperimentStepMessageMapper stepMessageMapper;

    @Inject
    RunMessageMetricMapper metricMapper;

    @Inject
    WebsocketSender websocketSender;

    @Inject
    WebsocketClientNotificationBO websocketClientNotificationBO;

    @Inject
    PermissionBO permissionBO;

    @Inject
    FLNetClientConfig config;

    @Transactional
    public void createAndCheckAutoAccessTransactional(ProjectFederatedRequestRunMetricsDTO request) {
        if (!config.request().runMetrics().enabled()) {
            Log.infof("Run-metrics request received but feature is disabled. Ignoring request %s", request.getGlobalRequestId());
            return;
        }
        Optional<FederatedLearningProjectEntity> projectOpt = projectAO.findByRequestIdOptional(request.getGlobalExperimentUniqueId());
        if (projectOpt.isEmpty()) {
            Log.errorf("Project for experiment %s not found — ignoring run-metrics request", request.getGlobalExperimentUniqueId());
            return;
        }
        RequestRunMetricsEntity entity = new RequestRunMetricsEntity();
        entity.setRequestKeycloakId(request.getKeycloakId());
        entity.setGlobalRequestId(request.getGlobalRequestId());
        entity.setProject(projectOpt.get());
        ao.persist(entity);

        // Mirrors the data-statistics / training auto-access path: if every cohort backing this
        // experiment grants the requesting user run-metrics auto access, approve and send the metrics
        // immediately; otherwise leave it PENDING and notify a human to decide.
        Set<Long> cohortIds = getCohortIds(entity);
        if (hasAutoMetricsAccessForAll(request.getKeycloakId(), cohortIds)) {
            Log.infof("Run-metrics auto access for user %s on request %s — auto-approving", request.getKeycloakId(), request.getGlobalRequestId());
            entity.setStatus(FederatedLearningRequestStatus.APPROVED);
            entity.setVerifiedOn(new Date());
            ao.persist(entity);
            sendMetricsResponse(entity);
        } else {
            websocketClientNotificationBO.notifyRunMetricsRequestReceived(cohortIds);
        }
    }

    private boolean hasAutoMetricsAccessForAll(String keycloakId, Set<Long> cohortIds) {
        if (cohortIds == null || cohortIds.isEmpty()) {
            return false;
        }
        return cohortIds.stream().allMatch(cohortId -> permissionBO.hasAutoMetricsAccess(keycloakId, cohortId));
    }

    public PagedResponse<RequestRunMetricsDTO> list(Page page, FederatedLearningRequestStatus status) {
        List<RequestRunMetricsEntity> entities = ao.list(page, status);
        List<RequestRunMetricsDTO> dtos = mapper.entitiesToDtos(entities);
        return new PagedResponse<>(dtos, page.index, page.size);
    }

    public List<SearchResultDTO<RequestRunMetricsDTO>> search(String query, String keycloakId) {
        return ao.listAll().stream()
                .filter(entity -> hasCohortSearchAccess(getCohortIds(entity), keycloakId))
                .map(entity -> {
                    RequestRunMetricsDTO dto = toSearchDTO(entity);
                    String title = "Metric request " + dto.getGlobalRequestId();
                    int score = SearchScoreUtil.score(query, title, dto.getProjectName(),
                            dto.getExperimentGlobalUniqueId(), String.valueOf(dto.getStatus()));
                    return SearchScoreUtil.toResult(SearchResultType.METRIC_REQUEST, dto, title, score);
                })
                .filter(result -> result.getScore() > 0)
                .toList();
    }

    private RequestRunMetricsDTO toSearchDTO(RequestRunMetricsEntity entity) {
        RequestRunMetricsDTO dto = new RequestRunMetricsDTO();
        dto.setId(entity.getId());
        dto.setVersion(entity.getVersion());
        dto.setRequestKeycloakId(entity.getRequestKeycloakId());
        dto.setGlobalRequestId(entity.getGlobalRequestId());
        dto.setVerifiedOn(entity.getVerifiedOn());
        dto.setVerifiedByKeycloakId(entity.getVerifiedByKeycloakId());
        dto.setStatus(entity.getStatus());
        if (entity.getProject() != null) {
            dto.setProjectId(entity.getProject().getId());
            dto.setProjectName(entity.getProject().getName());
            if (entity.getProject().getRequest() != null) {
                dto.setExperimentGlobalUniqueId(entity.getProject().getRequest().getGlobalFLExperimentUniqueId());
            }
        }
        return dto;
    }

    private boolean hasCohortSearchAccess(Set<Long> cohortIds, String keycloakId) {
        return cohortIds != null && !cohortIds.isEmpty() && cohortIds.stream()
                .allMatch(cohortId -> cohortMemberAuthBO.isMember(cohortId, keycloakId));
    }

    public Set<Long> getCohortIds(Long id) {
        RequestRunMetricsEntity entity = ao.findByIdOptional(id)
                .orElseThrow(() -> new NotFoundException("RequestRunMetrics not found: " + id));
        return getCohortIds(entity);
    }

    private Set<Long> getCohortIds(RequestRunMetricsEntity entity) {
        if (entity.getProject() == null ||
                entity.getProject().getRequest() == null ||
                entity.getProject().getRequest().getPatients() == null) {
            return Set.of();
        }
        return entity.getProject().getRequest().getPatients().stream()
                .map(patientLearning -> patientLearning.getPatient().getCohort().getId())
                .collect(Collectors.toSet());
    }

    @Transactional
    public RequestRunMetricsDTO update(Long id, RequestRunMetricsDTO updateDTO, String keycloakId) {
        RequestRunMetricsEntity entity = ao.findByIdOptional(id)
                .orElseThrow(() -> new NotFoundException("RequestRunMetrics not found: " + id));

        if (!entity.getStatus().equals(FederatedLearningRequestStatus.PENDING)) {
            throw new IllegalArgumentException("Status has already been decided");
        }
        entity.setStatus(updateDTO.getStatus());
        entity.setVerifiedOn(new Date());
        entity.setVerifiedByKeycloakId(keycloakId);
        ao.persist(entity);

        if (entity.getStatus().equals(FederatedLearningRequestStatus.APPROVED)) {
            sendMetricsResponse(entity);
        }
        return mapper.entityToDto(entity);
    }

    private void sendMetricsResponse(RequestRunMetricsEntity entity) {
        FederatedLearningProjectEntity project = entity.getProject();
        FederatedLearningExperimentEntity experiment = project.getExperiment();
        if (experiment == null) {
            Log.errorf("No experiment found for project %d — cannot send metrics", project.getId());
            return;
        }

        List<RunMessageMetricDTO> metricMessages = experiment.getSteps().stream()
                .flatMap(step -> metricMapper.dtosToMetricDtos(
                        stepMessageMapper.entitiesToDtos(
                                stepMessageAO.findByStep(step.getId(), RunMessageTypes.METRIC)
                        )
                ).stream())
                .collect(Collectors.toList());

        List<RunMetricDTO> metrics = metricMessages.stream()
                .map(m -> new RunMetricDTO(m.getMetric(), m.getValue(), m.getX(), m.getXUnit()))
                .collect(Collectors.toList());

        RunMetricsResponseClientDTO response = new RunMetricsResponseClientDTO();
        response.setGlobalExperimentUniqueId(project.getRequest().getGlobalFLExperimentUniqueId());
        response.setRandomClinicId(experiment.getUniqueRandomClinicId());
        response.setRequestId(entity.getGlobalRequestId());
        response.setMetrics(metrics);

        websocketSender.sendRunMetricsClientResponse(response);
    }
}
