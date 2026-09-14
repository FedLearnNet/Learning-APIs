package bio.cosy.feddb.local.api.learning.request;

import bio.cosy.feddb.core.api.socket.LearningQueryClientResponseDTO;
import bio.cosy.feddb.core.api.socket.ProjectFederatedExperimentForLocalDTO;
import bio.cosy.feddb.core.api.workflow.node.WorkflowNodeDetailDTO;
import bio.cosy.feddb.core.base.BaseBo;
import bio.cosy.feddb.core.base.PagedResponse;
import bio.cosy.feddb.local.api.cohort.member.CohortMemberAuthBO;
import bio.cosy.feddb.local.api.cohort.patient.traceability.learning.PatientLearningBO;
import bio.cosy.feddb.local.api.cohort.patient.traceability.learning.PatientLearningDTO;
import bio.cosy.feddb.local.api.eam.WebsocketSender;
import bio.cosy.feddb.local.api.learning.project.FederatedLearningProjectBO;
import bio.cosy.feddb.local.api.learning.project.FederatedLearningProjectEntity;
import bio.cosy.feddb.local.api.learning.project.run.FederatedLearningExperimentBO;
import bio.cosy.feddb.local.api.learning.request.cohort.FederatedLearningRequestCohortBO;
import bio.cosy.feddb.local.api.learning.request.cohort.FederatedLearningRequestCohortDTO;
import bio.cosy.feddb.local.api.learning.request.cohort.FederatedLearningRequestCohortStatus;
import bio.cosy.feddb.local.api.notification.WebsocketClientNotificationBO;
import bio.cosy.feddb.local.api.privacy.PrivacyBO;
import bio.cosy.feddb.local.api.query.LocalQueryDTO;
import bio.cosy.feddb.local.api.query.QueryBO;
import bio.cosy.feddb.local.api.query.QueryResultWrapperDTO;
import bio.cosy.feddb.local.api.search.SearchResultDTO;
import bio.cosy.feddb.local.api.search.SearchResultType;
import bio.cosy.feddb.local.api.search.SearchScoreUtil;
import io.quarkus.logging.Log;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.panache.common.Page;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotAllowedException;
import jakarta.ws.rs.NotFoundException;

import java.util.*;
import java.util.stream.Collectors;

@ApplicationScoped
public class FederatedLearningRequestBO extends BaseBo<FederatedLearningRequestDTO, FederatedLearningRequestEntity, FederatedLearningRequestAO, FederatedLearningRequestMapper> {

    @Inject
    QueryBO queryBO;

    @Inject
    CohortMemberAuthBO cohortMemberAuthBO;

    @Inject
    PatientLearningBO patientLearningBO;

    @Inject
    FederatedLearningProjectBO projectBO;

    @Inject
    WebsocketSender websocketSender;

    @Inject
    FederatedLearningExperimentBO experimentBO;

    @Inject
    PrivacyBO privacyBO;

    @Inject
    WebsocketClientNotificationBO websocketClientNotificationBO;

    @Inject
    FederatedLearningRequestCohortBO cohortDecisionBO;

    public PagedResponse<FederatedLearningRequestDTO> list(Page page, FederatedLearningRequestStatus status, String keycloakId) {
        List<FederatedLearningRequestDTO> found = ao.list(page, status).stream()
                .filter(entity -> hasCohortSearchAccess(getInvolvedCohortIds(entity), keycloakId))
                .map(entity -> toCohortScopedDto(entity, keycloakId))
                .filter(dto -> dto.getStatus() != FederatedLearningRequestStatus.PENDING
                        || dto.isAwaitingCurrentUserDecision())
                .toList();
        return new PagedResponse<>(found, page.index, page.size);
    }

    public FederatedLearningRequestDTO findById(Long id) {
        return getById(id);
    }

    public FederatedLearningRequestDTO findByIdForUser(Long id, String keycloakId) {
        FederatedLearningRequestEntity entity = ao.findByIdOptional(id)
                .orElseThrow(() -> new NotFoundException("FederatedLearningRequest not found"));
        return toCohortScopedDto(entity, keycloakId);
    }

    public List<SearchResultDTO<FederatedLearningRequestDTO>> search(String query, String keycloakId) {
        return ao.listAll().stream()
                .filter(entity -> hasCohortSearchAccess(getInvolvedCohortIds(entity), keycloakId))
                .map(entity -> toCohortScopedDto(entity, keycloakId))
                .filter(dto -> dto.getStatus() != FederatedLearningRequestStatus.PENDING
                        || dto.isAwaitingCurrentUserDecision())
                .map(dto -> {
                    String title = "Training request " + dto.getGlobalFLExperimentUniqueId();
                    int score = SearchScoreUtil.score(query, title, String.valueOf(dto.getStatus()));
                    return SearchScoreUtil.toResult(SearchResultType.TRAINING_REQUEST, dto, title, score);
                })
                .filter(result -> result.getScore() > 0)
                .toList();
    }

    private Set<Long> getPatientCohortIds(FederatedLearningRequestEntity entity) {
        if (entity.getPatients() == null) {
            return Set.of();
        }
        return entity.getPatients().stream()
                .map(patientLearning -> patientLearning.getPatient().getCohort().getId())
                .collect(Collectors.toSet());
    }

    private Set<Long> getInvolvedCohortIds(FederatedLearningRequestEntity entity) {
        return cohortDecisionBO.getCohortIds(entity.getId());
    }

    private boolean hasCohortSearchAccess(Set<Long> cohortIds, String keycloakId) {
        return cohortIds != null && !cohortIds.isEmpty() && cohortIds.stream()
                .anyMatch(cohortId -> cohortMemberAuthBO.isMember(cohortId, keycloakId));
    }

    private FederatedLearningRequestDTO toCohortScopedDto(FederatedLearningRequestEntity entity,
                                                          String keycloakId) {
        FederatedLearningRequestDTO dto = mapper.entityToDto(entity);

        Set<Long> accessibleCohortIds = getInvolvedCohortIds(entity).stream()
                .filter(cohortId -> cohortMemberAuthBO.isMember(cohortId, keycloakId))
                .collect(Collectors.toSet());

        if (dto.getRequestPatients() != null) {
            dto.setRequestPatients(dto.getRequestPatients().stream()
                    .filter(patient -> accessibleCohortIds.contains(patient.getInternalCohortId()))
                    .toList());
        }

        List<FederatedLearningRequestCohortDTO> decisions =
                cohortDecisionBO.findByRequestForUser(entity.getId(), keycloakId).stream()
                        .filter(decision -> accessibleCohortIds.contains(decision.getCohortId()))
                        .toList();
        dto.setCohortDecisions(decisions);
        dto.setAwaitingCurrentUserDecision(
                decisions.stream().anyMatch(FederatedLearningRequestCohortDTO::isDecidableByCurrentUser));
        return dto;
    }

    public void checkForCohortAccess(Long id, String keycloakId) {
        if (!hasCohortSearchAccess(getCohortIds(id), keycloakId)) {
            throw new NotAllowedException("You are not allowed to access this training request");
        }
    }

    public Set<Long> getCohortIds(Long id) {
        FederatedLearningRequestEntity entity = ao.findByIdOptional(id)
                .orElseThrow(() -> new NotFoundException("FederatedLearningRequest not found"));
        Set<Long> decisionCohorts = getInvolvedCohortIds(entity);
        return decisionCohorts.isEmpty() ? getPatientCohortIds(entity) : decisionCohorts;
    }

    public Optional<LearningQueryClientResponseDTO> handleLearningRequest(ProjectFederatedExperimentForLocalDTO requestLearning) {
        Log.infof("Processing requesting learning: %s", requestLearning);

        String queryId = requestLearning.getProjectVersion().getGlobalUniqueQueryId();
        if (queryId == null) {
            Log.warnf("Stop processing requesting learning: %s, query id is null", requestLearning);
            return Optional.of(LearningQueryClientResponseDTO.createErrorResponse("Query ID is null", requestLearning.getGlobalUniqueId()));
        }

        Optional<LocalQueryDTO> queryOptional = queryBO.findByGlobalIdOptionalTransactional(queryId);
        if (queryOptional.isEmpty()) {
            Log.warnf("Stop processing requesting learning: %s, query not found", requestLearning);
            return Optional.of(LearningQueryClientResponseDTO.createErrorResponse("Query not found", requestLearning.getGlobalUniqueId()));
        }

        QueryResultWrapperDTO harmonizedQueryResult = queryBO.runQuery(queryOptional.get(), requestLearning.getKeycloakId());
        if (harmonizedQueryResult == null) {
            Log.warnf("Stop processing requesting learning: %s, query result is null", requestLearning);
            return Optional.of(LearningQueryClientResponseDTO.createErrorResponse("Query Error", requestLearning.getGlobalUniqueId()));
        }

        Log.info("Received query result: " + harmonizedQueryResult);

        int certificationLevel = requestLearning.getProjectVersion() != null
                ? requestLearning.getProjectVersion().getCertificationLevel() : 0;

        boolean needsInternetAccess = requestLearning.getWorkflow().getNodes().stream()
                .filter(Objects::nonNull)
                .anyMatch(WorkflowNodeDetailDTO::needsInternetAccess);

        Long id = createTransactional(queryOptional.get(), requestLearning, harmonizedQueryResult);

        Set<Long> pendingCohortIds = initCohortDecisionsTransactional(
                id, requestLearning.getKeycloakId(), certificationLevel, needsInternetAccess);

        // Auto-approved cohorts: send their patients to global now. Request stays PENDING while any
        // cohort is still manual; flips to APPROVED only when every cohort is decided.
        QuarkusTransaction.requiringNew().run(() -> finalizeAutoApprovedCohorts(id));

        if (!pendingCohortIds.isEmpty()) {
            websocketClientNotificationBO.notifyTrainingRequestReceived(pendingCohortIds);
        }
        return Optional.empty();
    }

    @Transactional
    public Long createTransactional(LocalQueryDTO query, ProjectFederatedExperimentForLocalDTO requestLearning,
                                    QueryResultWrapperDTO harmonizedQueryResult) {
        FederatedLearningRequestDTO requestLearningDTO = new FederatedLearningRequestDTO();
        requestLearningDTO.setStatus(FederatedLearningRequestStatus.PENDING);
        requestLearningDTO.setGlobalFLExperimentUniqueId(requestLearning.getGlobalUniqueId());
        FederatedLearningRequestEntity entity = mapper.dtoToEntity(requestLearningDTO);

        entity.setName(requestLearning.getName());
        entity.setDescription(requestLearning.getDescription());
        entity.setPlatformUserId(requestLearning.getKeycloakId());
        entity.setModelNeedToBePublic(requestLearning.getModelNeedToBePublic());

        ao.persist(entity);

        FederatedLearningProjectEntity project = projectBO.createForRequest(
                query.getId(), entity, requestLearning.getProjectVersion(), requestLearning.getWorkflow());
        entity.setProject(project);

        patientLearningBO.initPatientList(query.getId(), entity, harmonizedQueryResult);
        return entity.getId();
    }

    @Transactional
    public Set<Long> initCohortDecisionsTransactional(Long id, String platformUserId,
                                                      int certificationLevel,
                                                      boolean needsInternetAccess) {
        FederatedLearningRequestEntity entity = ao.findByIdOptional(id)
                .orElseThrow(() -> new NotFoundException("FederatedLearningRequest not found"));
        Set<Long> cohortIds = patientLearningBO.getCohortIdsForRequests(List.of(id));
        if (cohortIds.isEmpty()) {
            cohortIds = getPatientCohortIds(entity);
        }
        return cohortDecisionBO.initForRequest(entity, cohortIds, platformUserId,
                certificationLevel, needsInternetAccess);
    }


    public FederatedLearningRequestDTO update(Long id, FederatedLearningRequestDTO updateDTO,
                                              String keycloakId) {
        FederatedLearningRequestStatus requested = updateDTO.getStatus();
        if (requested != FederatedLearningRequestStatus.APPROVED
                && requested != FederatedLearningRequestStatus.REJECTED) {
            throw new IllegalArgumentException("A training request can only be approved or rejected");
        }

        FederatedLearningRequestEntity entity = ao.findByIdOptional(id)
                .orElseThrow(() -> new NotFoundException("FederatedLearningRequest not found"));

        if (entity.getStatus() != FederatedLearningRequestStatus.PENDING) {
            throw new IllegalArgumentException("Status has already been changed");
        }
        if (requested == FederatedLearningRequestStatus.APPROVED
                && Boolean.TRUE.equals(entity.getModelNeedToBePublic())
                && Boolean.FALSE.equals(updateDTO.getModelCanBePublic())) {
            throw new IllegalArgumentException("The model of this request needs to be public");
        }

        Set<Long> decidableCohortIds = cohortDecisionBO.getDecidableCohortIds(id, keycloakId);
        if (decidableCohortIds.isEmpty()) {
            throw new ForbiddenException("You have no pending cohort to decide on this request");
        }

        FederatedLearningRequestCohortStatus decision =
                requested == FederatedLearningRequestStatus.APPROVED
                        ? FederatedLearningRequestCohortStatus.APPROVED
                        : FederatedLearningRequestCohortStatus.REJECTED;

        if (cohortDecisionBO.decidePending(id, decidableCohortIds, decision) == 0) {
            throw new IllegalArgumentException("Status has already been changed");
        }

        if (decision == FederatedLearningRequestCohortStatus.REJECTED) {
            patientLearningBO.removePatientsOfCohorts(id, decidableCohortIds);
        } else if (updateDTO.getRequestPatients() != null) {
            Set<Long> keepIds = updateDTO.getRequestPatients().stream()
                    .map(PatientLearningDTO::getId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            patientLearningBO.retainPatients(id, decidableCohortIds, keepIds);
        }

        if (decision == FederatedLearningRequestCohortStatus.APPROVED) {
            long acceptedNow = patientLearningBO.countForRequestAndCohorts(id, decidableCohortIds);
            if (acceptedNow > 0) {
                notifyAcceptedPatients(id, acceptedNow, updateDTO.getModelCanBePublic());
            } else if (Boolean.FALSE.equals(updateDTO.getModelCanBePublic())) {
                applyModelCanBePublicVeto(id);
            }
        } else if (Boolean.FALSE.equals(updateDTO.getModelCanBePublic())) {
            applyModelCanBePublicVeto(id);
        }

        resolveRequestStatus(id, updateDTO.getModelCanBePublic());
        return findByIdForUser(id, keycloakId);
    }

    private void finalizeAutoApprovedCohorts(Long id) {
        Set<Long> autoApprovedCohortIds = cohortDecisionBO.getApprovedCohortIds(id);
        long autoAcceptedPatients = patientLearningBO.countForRequestAndCohorts(id, autoApprovedCohortIds);
        if (autoAcceptedPatients > 0) {
            notifyAcceptedPatients(id, autoAcceptedPatients, null);
        }
        resolveRequestStatus(id, null);
    }

    private void notifyAcceptedPatients(Long id, long patientCount, Boolean modelCanBePublic) {
        FederatedLearningRequestEntity entity = ao.findByIdForUpdate(id)
                .orElseThrow(() -> new NotFoundException("FederatedLearningRequest not found"));
        if (Boolean.FALSE.equals(modelCanBePublic)) {
            entity.setModelCanBePublic(false);
        }

        String globalId = entity.getGlobalFLExperimentUniqueId();
        int count = privacyBO.modifyQueryCount(patientCount).intValue();
        String clinicId = experimentBO.ensureApprovedLearning(entity).getUniqueRandomClinicId();
        Log.infof("Notify accepted patients for request %s: count=%d (raw=%d), clinic=%s",
                id, count, patientCount, clinicId);
        websocketSender.sendLearningClientResponse(count, globalId, clinicId, entity.getModelCanBePublic());

        List<String> currentIds = new ArrayList<>(findApprovedAndRunningIds());
        if (!currentIds.contains(globalId)) {
            currentIds.add(globalId);
        }
        websocketSender.sendCurrentLearnings(currentIds);
    }

    @Transactional
    public void applyModelCanBePublicVeto(String globalFLExperimentUniqueId) {
        ao.findByGlobalFLExperimentUniqueId(globalFLExperimentUniqueId)
                .ifPresent(entity -> entity.setModelCanBePublic(false));
    }

    private void applyModelCanBePublicVeto(Long id) {
        FederatedLearningRequestEntity entity = ao.findByIdForUpdate(id)
                .orElseThrow(() -> new NotFoundException("FederatedLearningRequest not found"));
        entity.setModelCanBePublic(false);
    }

    private void resolveRequestStatus(Long id, Boolean modelCanBePublic) {
        FederatedLearningRequestEntity entity = ao.findByIdForUpdate(id)
                .orElseThrow(() -> new NotFoundException("FederatedLearningRequest not found"));

        if (Boolean.FALSE.equals(modelCanBePublic)) {
            entity.setModelCanBePublic(false);
        }
        if (entity.getStatus() != FederatedLearningRequestStatus.PENDING
                || cohortDecisionBO.hasPending(id)
                || cohortDecisionBO.getCohortIds(id).isEmpty()) {
            return;
        }

        long remainingPatients = patientLearningBO.countForRequest(id);
        if (!cohortDecisionBO.hasApproved(id) || remainingPatients == 0) {
            entity.setStatus(FederatedLearningRequestStatus.REJECTED);
            return;
        }

        entity.setStatus(FederatedLearningRequestStatus.APPROVED);
        entity.setModelCanBePublic(!Boolean.FALSE.equals(entity.getModelCanBePublic()));
    }

    public void delete(Long id) {
        ao.deleteById(id);
    }

    public void listExists(List<Long> requestIds) {
        for (Long id : requestIds) {
            findById(id);
        }
    }

    public List<String> findApprovedAndRunningIds() {
        return ao.findApprovedAndRunning().stream()
                .map(FederatedLearningRequestEntity::getGlobalFLExperimentUniqueId)
                .toList();
    }

    @Transactional
    public void finishLearning(String globalUniqueQueryId) {
        updateLearningStatus(globalUniqueQueryId, FederatedLearningRequestStatus.COMPLETED);
    }

    @Transactional
    public void stopLearning(String globalUniqueQueryId) {
        updateLearningStatus(globalUniqueQueryId, FederatedLearningRequestStatus.STOPPED);
    }

    private void updateLearningStatus(String globalUniqueQueryId, FederatedLearningRequestStatus status) {
        if (!ao.updateStatusTransactional(globalUniqueQueryId, status)) {
            throw new NotFoundException("FederatedLearningRequest not found");
        }
    }
}
