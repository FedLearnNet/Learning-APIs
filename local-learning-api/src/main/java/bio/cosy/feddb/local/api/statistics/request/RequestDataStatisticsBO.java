package bio.cosy.feddb.local.api.statistics.request;

import bio.cosy.feddb.core.api.socket.DataStatisticsResponseClientDTO;
import bio.cosy.feddb.core.api.socket.ProjectFederatedRequestDataStatisticsDTO;
import bio.cosy.feddb.core.base.BaseBo;
import bio.cosy.feddb.core.base.PagedResponse;
import bio.cosy.feddb.local.api.cohort.member.CohortMemberAuthBO;
import bio.cosy.feddb.local.api.cohort.patient.PatientAO;
import bio.cosy.feddb.local.api.cohort.patient.PatientEntity;
import bio.cosy.feddb.local.api.cohort.patient.traceability.query.QueryPatientEntity;
import bio.cosy.feddb.local.api.cohort.permission.PermissionBO;
import bio.cosy.feddb.local.api.eam.WebsocketSender;
import bio.cosy.feddb.local.api.learning.request.FederatedLearningRequestStatus;
import bio.cosy.feddb.local.api.notification.WebsocketClientNotificationBO;
import bio.cosy.feddb.local.api.privacy.PrivacyStatisticsBO;
import bio.cosy.feddb.local.api.query.QueryAO;
import bio.cosy.feddb.local.api.query.QueryEntity;
import bio.cosy.feddb.local.api.search.SearchResultDTO;
import bio.cosy.feddb.local.api.search.SearchResultType;
import bio.cosy.feddb.local.api.search.SearchScoreUtil;
import bio.cosy.feddb.local.api.statistics.DataStatisticsBO;
import bio.cosy.feddb.local.api.statistics.LocalDataStatisticsDTO;
import bio.cosy.feddb.local.config.FLNetClientConfig;
import io.quarkus.logging.Log;
import io.quarkus.panache.common.Page;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;

import java.util.*;
import java.util.stream.Collectors;

@ApplicationScoped
public class RequestDataStatisticsBO extends BaseBo<RequestDataStatisticsDTO, RequestDataStatisticsEntity, RequestDataStatisticsAO, RequestDataStatisticsMapper> {


    @Inject
    DataStatisticsBO dataStatisticsBO;

    @Inject
    CohortMemberAuthBO cohortMemberAuthBO;

    @Inject
    PrivacyStatisticsBO privacyStatisticsBO;

    @Inject
    PermissionBO permissionBO;

    @Inject
    WebsocketSender websocketSender;

    @Inject
    QueryAO queryAO;

    @Inject
    PatientAO patientAO;

    @Inject
    FLNetClientConfig config;

    @Inject
    WebsocketClientNotificationBO websocketClientNotificationBO;

    private RequestDataStatisticsEntity createEntity(ProjectFederatedRequestDataStatisticsDTO request) {
        Optional<QueryEntity> queryOpt = queryAO.findByGlobalId(request.getGlobalUniqueQueryId());
        if (queryOpt.isEmpty()) {
            Log.errorf("Query with global id %s not found. Cannot create RequestDataStatisticsEntity.", request.getGlobalUniqueQueryId());
            return null;
        }
        RequestDataStatisticsEntity entity = new RequestDataStatisticsEntity();
        entity.setRequestId(request.getGlobalRequestId());
        entity.setRequestKeycloakId(request.getKeycloakId());
        entity.setQuery(queryOpt.get());
        ao.persist(entity);
        return entity;
    }

    @Transactional
    public void createAndCheckAutoAccessTransactional(ProjectFederatedRequestDataStatisticsDTO request) {
        if (!config.request().dataStatistics().enabled()) {
            Log.infof("Data statistics request received but feature is disabled. Ignoring request for global query id %s", request.getGlobalUniqueQueryId());
            return;
        }

        boolean responseStillNeeded = checkForAutoStatisticsAccess(
                request.getKeycloakId(),
                request.getGlobalUniqueQueryId(),
                request.getGlobalRequestId());
        boolean hasAutoAccessForAll = allCohortIdsHaveAutoAccess(
                request.getKeycloakId(),
                request.getGlobalUniqueQueryId());
        RequestDataStatisticsEntity entity = createEntity(request);
        if (entity == null) {
            return;
        }

        if (hasAutoAccessForAll) {
            entity.setStatus(FederatedLearningRequestStatus.APPROVED);
            entity.setVerifiedOn(new Date());
            ao.persist(entity);
            if (responseStillNeeded) {
                notifyStatisticsRequestStatusUpdate(entity);
            }
        } else {
            websocketClientNotificationBO.notifyStatisticsRequestReceived(getCohortIds(entity));
        }
    }

    @Transactional
    public boolean allCohortIdsHaveAutoAccess(String keycloakId, String queryId) {
        return queryAO.findByGlobalId(queryId)
                .map(query -> {
                    Set<Long> ids = query.getQueriedCohortIds();
                    if (ids == null || ids.isEmpty()) return false;
                    return ids.stream().allMatch(id -> permissionBO.hasAutoStatisticsAccess(keycloakId, id));
                })
                .orElse(false);
    }

    @Transactional
    public void autoApproveTransactional(UUID requestId) {
        ao.find("requestId = ?1", requestId)
                .firstResultOptional()
                .ifPresent(entity -> {
                    entity.setStatus(FederatedLearningRequestStatus.APPROVED);
                    entity.setVerifiedOn(new Date());
                    ao.persist(entity);
                    notifyStatisticsRequestStatusUpdate(entity);
                });
    }

    @Transactional
    public boolean checkForAutoStatisticsAccess(String keycloakId, String queryId, UUID requestId) {
        Optional<QueryEntity> queryOpt = queryAO.findByGlobalId(queryId);
        if (queryOpt.isEmpty()) {
            Log.errorf("Query with global id %s not found. Cannot create RequestDataStatisticsEntity.", queryId);
            return false;
        }
        QueryEntity query = queryOpt.get();
        Map<Long, Set<PatientEntity>> cohortPatientMap = Optional.ofNullable(query.getPatients())
                .map(patients -> patients.stream()
                        .collect(Collectors.groupingBy(
                                qp -> qp.getPatient().getCohort().getId(),
                                Collectors.mapping(QueryPatientEntity::getPatient, Collectors.toSet())
                        )))
                .orElse(Map.of());

        // QueryPatientEntity records are written asynchronously — fall back to queriedCohortIds
        // (written synchronously in handleQuery) when patients aren't stored yet
        Set<Long> cohortIds = cohortPatientMap.isEmpty() ? query.getQueriedCohortIds() : cohortPatientMap.keySet();
        if (cohortIds == null || cohortIds.isEmpty()) {
            Log.warnf("No cohort data for query %s — statistics request dropped", queryId);
            return false;
        }

        boolean needToCreate = false;
        for (Long cohortId : cohortIds) {
            Set<PatientEntity> patients = cohortPatientMap.get(cohortId);
            if (permissionBO.hasAutoStatisticsAccess(keycloakId, cohortId)) {
                Log.infof("Statistics auto access for user %s on query %s enabled", keycloakId, queryId);
                if (patients != null && !patients.isEmpty()) {
                    notifyStatisticsRequestStatusUpdate(patients, queryId, requestId);
                } else {
                    // Patients not yet persisted by async task — create pending so request isn't dropped
                    needToCreate = true;
                }
            } else {
                needToCreate = true;
            }
        }
        return needToCreate;
    }

    public PagedResponse<RequestDataStatisticsDTO> list(
            Page page,
            FederatedLearningRequestStatus status) {

        List<RequestDataStatisticsEntity> foundEntity = status == null
                ? ao.list(page)
                : ao.list(page, status);
        List<RequestDataStatisticsDTO> found = mapper.entitiesToDtos(foundEntity);
        return new PagedResponse<>(found, page.index, page.size);
    }

    public PagedResponse<RequestDataStatisticsDTO> list(
            Page page,
            FederatedLearningRequestStatus status,
            Long patientId) {

        List<RequestDataStatisticsEntity> foundEntity = status == null
                ? ao.list(page, patientId)
                : ao.list(page, status, patientId);
        List<RequestDataStatisticsDTO> found = mapper.entitiesToDtos(foundEntity);
        return new PagedResponse<>(found, page.index, page.size);
    }

    public List<SearchResultDTO<RequestDataStatisticsDTO>> search(String query, String keycloakId) {
        return ao.listAll().stream()
                .filter(entity -> hasCohortSearchAccess(getCohortIds(entity), keycloakId))
                .map(entity -> {
                    RequestDataStatisticsDTO dto = toSearchDTO(entity);
                    String title = "Statistics request " + dto.getRequestId();
                    int score = SearchScoreUtil.score(query, title, dto.getRequestKeycloakId(),
                            String.valueOf(dto.getStatus()), String.valueOf(dto.getQueryId()));
                    return SearchScoreUtil.toResult(SearchResultType.STATISTICS_REQUEST, dto, title, score);
                })
                .filter(result -> result.getScore() > 0)
                .toList();
    }

    private RequestDataStatisticsDTO toSearchDTO(RequestDataStatisticsEntity entity) {
        RequestDataStatisticsDTO dto = new RequestDataStatisticsDTO();
        dto.setId(entity.getId());
        dto.setVersion(entity.getVersion());
        dto.setRequestKeycloakId(entity.getRequestKeycloakId());
        dto.setVerifiedOn(entity.getVerifiedOn());
        dto.setVerifiedByKeycloakId(entity.getVerifiedByKeycloakId());
        dto.setStatus(entity.getStatus());
        dto.setRequestId(entity.getRequestId());
        dto.setQueryId(entity.getQuery() == null ? null : entity.getQuery().getId());
        return dto;
    }

    private boolean hasCohortSearchAccess(Set<Long> cohortIds, String keycloakId) {
        return cohortIds != null && !cohortIds.isEmpty() && cohortIds.stream()
                .allMatch(cohortId -> cohortMemberAuthBO.isMember(cohortId, keycloakId));
    }

    public RequestDataStatisticsDTO update(Long id, RequestDataStatisticsDTO updateDTO, String keycloakId) {
        Optional<RequestDataStatisticsEntity> entityOptional = ao.findByIdOptional(id);
        if (entityOptional.isPresent()) {
            RequestDataStatisticsEntity entity = entityOptional.get();
            if (!entity.getStatus().equals(FederatedLearningRequestStatus.PENDING)) {
                throw new IllegalArgumentException("Status has already been changed");
            }
            entity.setStatus(updateDTO.getStatus());
            entity.setVerifiedOn(new Date());
            entity.setVerifiedByKeycloakId(keycloakId);
            ao.persist(entity);
            if (entity.getStatus().equals(FederatedLearningRequestStatus.APPROVED)) {
                notifyStatisticsRequestStatusUpdate(entity);
            }
            return mapper.entityToDto(entity);
        }
        throw new NotFoundException("FederatedLearningRequest not found");
    }

    public Set<Long> getCohortIds(Long id) {
        RequestDataStatisticsEntity entity = ao.findByIdOptional(id)
                .orElseThrow(() -> new NotFoundException("FederatedLearningRequest not found"));
        return getCohortIds(entity);
    }

    private Set<Long> getCohortIds(RequestDataStatisticsEntity entity) {
        if (entity.getQuery() == null) {
            return Set.of();
        }

        Set<QueryPatientEntity> queryPatients = entity.getQuery().getPatients();
        if (queryPatients == null || queryPatients.isEmpty()) {
            return Optional.ofNullable(entity.getQuery().getQueriedCohortIds()).orElse(Set.of());
        }

        return queryPatients.stream()
                .map(QueryPatientEntity::getPatient)
                .map(patient -> patient.getCohort().getId())
                .collect(Collectors.toSet());
    }

    private void notifyStatisticsRequestStatusUpdate(RequestDataStatisticsEntity entity) {
        if (entity == null || entity.getQuery() == null ||
                entity.getQuery().getPatients() == null) {
            return;
        }
        Set<PatientEntity> patients = entity.getQuery().getPatients().stream()
                .map(QueryPatientEntity::getPatient)
                .collect(Collectors.toSet());

        if (config.request().dataStatistics().returnAllOnEmpty() && patients.isEmpty()) {
            patients = patientAO.findAll().stream().collect(Collectors.toSet());
        } else if (patients.isEmpty()) {
            return;
        }
        LocalDataStatisticsDTO statistics = privacyStatisticsBO.modifyStatistics(dataStatisticsBO.getDataStatisticsForPatient(patients));
        DataStatisticsResponseClientDTO response = new DataStatisticsResponseClientDTO(statistics,
                entity.getQuery().getGlobalQueryId(), UUID.randomUUID().toString(), entity.getRequestId()
        );
        Log.infof("Notify statistics for global Query: %s", response.getStatistics());
        websocketSender.sendDataStatisticsClientResponse(response);
    }

    private void notifyStatisticsRequestStatusUpdate(Set<PatientEntity> patients, String getGlobalQueryId, UUID requestId) {
        LocalDataStatisticsDTO statistics = privacyStatisticsBO.modifyStatistics(dataStatisticsBO.getDataStatisticsForPatient(patients));
        DataStatisticsResponseClientDTO response = new DataStatisticsResponseClientDTO(statistics,
                getGlobalQueryId, UUID.randomUUID().toString(), requestId
        );
        Log.infof("Notify statistics for global Query: %s", response.getStatistics());
        websocketSender.sendDataStatisticsClientResponse(response);
    }
}
