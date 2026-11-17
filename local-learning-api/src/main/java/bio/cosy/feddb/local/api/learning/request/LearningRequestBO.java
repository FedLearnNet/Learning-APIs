package bio.cosy.feddb.local.api.learning.request;

import bio.cosy.feddb.core.api.socket.LearningQueryClientResponseDTO;
import bio.cosy.feddb.core.execution.ExecutionMode;
import bio.cosy.feddb.core.api.socket.ProjectFederatedExperimentForLocalDTO;
import bio.cosy.feddb.core.api.tre.TreSourceDTO;
import bio.cosy.feddb.core.api.workflow.node.WorkflowNodeDetailDTO;
import bio.cosy.feddb.core.base.BaseBo;
import bio.cosy.feddb.core.base.PagedResponse;
import bio.cosy.feddb.local.api.cohort.TreSourceBO;
import bio.cosy.feddb.local.api.cohort.member.CohortMemberAuthBO;
import bio.cosy.feddb.local.api.cohort.patient.traceability.learning.PatientLearningBO;
import bio.cosy.feddb.local.api.cohort.patient.traceability.learning.PatientLearningDTO;
import bio.cosy.feddb.local.api.eam.WebsocketSender;
import bio.cosy.feddb.local.api.learning.project.LearningProjectBO;
import bio.cosy.feddb.local.api.learning.project.LearningProjectEntity;
import bio.cosy.feddb.local.api.learning.project.run.LearningRunBO;
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
public class LearningRequestBO extends BaseBo<LearningRequestDTO, LearningRequestEntity, LearningRequestAO, LearningRequestMapper> {

    @Inject
    QueryBO queryBO;

    @Inject
    CohortMemberAuthBO cohortMemberAuthBO;

    @Inject
    PatientLearningBO patientLearningBO;

    @Inject
    LearningProjectBO projectBO;

    @Inject
    WebsocketSender websocketSender;

    @Inject
    LearningRunBO learningRunBO;

    @Inject
    PrivacyBO privacyBO;

    @Inject
    WebsocketClientNotificationBO websocketClientNotificationBO;

    @Inject
    FederatedLearningRequestCohortBO cohortDecisionBO;

    @Inject
    TreSourceBO treSourceBO;

    public PagedResponse<LearningRequestDTO> list(Page page, LearningRequestStatus status, String keycloakId) {
        List<LearningRequestDTO> found = ao.list(page, status).stream()
                .filter(entity -> hasCohortSearchAccess(getInvolvedCohortIds(entity), keycloakId))
                .map(entity -> toSummaryDto(entity, keycloakId))
                .filter(dto -> dto.getStatus() != LearningRequestStatus.PENDING
                        || dto.isAwaitingCurrentUserDecision())
                .toList();
        return new PagedResponse<>(found, page.index, page.size);
    }

    public LearningRequestDTO findById(Long id) {
        return getById(id);
    }

    public LearningRequestDTO findByIdForUser(Long id, String keycloakId) {
        LearningRequestEntity entity = ao.findByIdOptional(id)
                .orElseThrow(() -> new NotFoundException("FederatedLearningRequest not found"));
        return toDetailDto(entity, keycloakId);
    }

    public List<SearchResultDTO<LearningRequestDTO>> search(String query, String keycloakId) {
        return ao.listAll().stream()
                .filter(entity -> hasCohortSearchAccess(getInvolvedCohortIds(entity), keycloakId))
                .map(entity -> toSummaryDto(entity, keycloakId))
                .filter(dto -> dto.getStatus() != LearningRequestStatus.PENDING
                        || dto.isAwaitingCurrentUserDecision())
                .map(dto -> {
                    String title = "Training request " + dto.getGlobalFLExperimentUniqueId();
                    int score = SearchScoreUtil.score(query, title, String.valueOf(dto.getStatus()));
                    return SearchScoreUtil.toResult(SearchResultType.TRAINING_REQUEST, dto, title, score);
                })
                .filter(result -> result.getScore() > 0)
                .toList();
    }

    private Set<Long> getPatientCohortIds(LearningRequestEntity entity) {
        TreSourceDTO treSource = entity.getProject() == null ? null : entity.getProject().getTreSource();
        if (treSource != null) {
            return treSourceBO.findCohortIdTransactional(treSource.getId()).map(Set::of).orElse(Set.of());
        }
        return patientLearningBO.getCohortIdsForRequests(List.of(entity.getId()));
    }

    private Set<Long> getInvolvedCohortIds(LearningRequestEntity entity) {
        return cohortDecisionBO.getCohortIds(entity.getId());
    }

    private boolean hasCohortSearchAccess(Set<Long> cohortIds, String keycloakId) {
        return cohortIds != null && !cohortIds.isEmpty() && cohortIds.stream()
                .anyMatch(cohortId -> cohortMemberAuthBO.isMember(cohortId, keycloakId));
    }

    private LearningRequestDTO toSummaryDto(LearningRequestEntity entity,
                                            String keycloakId) {
        LearningRequestDTO dto = mapper.entityToSummaryDto(entity);
        Set<Long> patientCohortScope = applyCohortScope(dto, entity, keycloakId);
        dto.setRequestPatients(List.of());
        dto.setPatientCountByCohort(
                patientLearningBO.countByCohortForRequest(entity.getId(), patientCohortScope));
        return dto;
    }

    private LearningRequestDTO toDetailDto(LearningRequestEntity entity,
                                           String keycloakId) {
        LearningRequestDTO dto = mapper.entityToDto(entity);
        Set<Long> patientCohortScope = applyCohortScope(dto, entity, keycloakId);
        if (dto.getRequestPatients() != null) {
            dto.setRequestPatients(dto.getRequestPatients().stream()
                    .filter(patient -> patientCohortScope.contains(patient.getInternalCohortId()))
                    .toList());
        }
        return dto;
    }

    /**
     * Applies shared cohort membership, decisions, and awaiting-decision flags.
     *
     * @return cohort ids used to scope patients or patient counts for this user
     */
    private Set<Long> applyCohortScope(LearningRequestDTO dto,
                                       LearningRequestEntity entity,
                                       String keycloakId) {
        Set<Long> accessibleCohortIds = getInvolvedCohortIds(entity).stream()
                .filter(cohortId -> cohortMemberAuthBO.isMember(cohortId, keycloakId))
                .collect(Collectors.toSet());

        List<FederatedLearningRequestCohortDTO> decisions =
                cohortDecisionBO.findByRequestForUser(entity.getId(), keycloakId).stream()
                        .filter(decision -> accessibleCohortIds.contains(decision.getCohortId()))
                        .toList();
        dto.setCohortDecisions(decisions);
        dto.setAwaitingCurrentUserDecision(
                decisions.stream().anyMatch(FederatedLearningRequestCohortDTO::isDecidableByCurrentUser));

        if (entity.getStatus() == LearningRequestStatus.PENDING) {
            return cohortDecisionBO.getDecidableCohortIds(entity.getId(), keycloakId);
        }
        return accessibleCohortIds;
    }

    public void checkForCohortAccess(Long id, String keycloakId) {
        if (!hasCohortSearchAccess(getCohortIds(id), keycloakId)) {
            throw new NotAllowedException("You are not allowed to access this training request");
        }
    }

    public Set<Long> getCohortIds(Long id) {
        LearningRequestEntity entity = ao.findByIdOptional(id)
                .orElseThrow(() -> new NotFoundException("FederatedLearningRequest not found"));
        Set<Long> decisionCohorts = getInvolvedCohortIds(entity);
        return decisionCohorts.isEmpty() ? getPatientCohortIds(entity) : decisionCohorts;
    }

    public Optional<LearningQueryClientResponseDTO> handleLearningRequest(ProjectFederatedExperimentForLocalDTO requestLearning) {
        Log.infof("Processing requesting learning: %s", requestLearning);

        TreSourceDTO treSource = requestLearning.getProjectVersion() == null ? null : requestLearning.getProjectVersion().getTreSource();
        if (treSource != null) {
            // sent to every client, only the one holding the database takes part
            if (treSourceBO.findCohortIdTransactional(treSource.getId()).isEmpty()) {
                return Optional.empty();
            }
            return decide(createTransactional(null, requestLearning, null), requestLearning);
        }

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
        return decide(createTransactional(queryOptional.get(), requestLearning, harmonizedQueryResult), requestLearning);
    }

    private Optional<LearningQueryClientResponseDTO> decide(Long id, ProjectFederatedExperimentForLocalDTO requestLearning) {
        int certificationLevel = requestLearning.getProjectVersion() != null
                ? requestLearning.getProjectVersion().getCertificationLevel() : 0;

        boolean needsInternetAccess = requestLearning.getWorkflow().getNodes().stream()
                .filter(Objects::nonNull)
                .anyMatch(WorkflowNodeDetailDTO::needsInternetAccess);

        Set<Long> pendingCohortIds = initCohortDecisionsTransactional(
                id, requestLearning.getKeycloakId(), requestLearning.getMode(), certificationLevel, needsInternetAccess);

        // Auto-approved cohorts: send their patients to global now. Request stays PENDING while any
        // cohort is still manual; flips to APPROVED only when every cohort is decided.
        finalizeAutoApprovedCohortsTransactional(id);

        if (!pendingCohortIds.isEmpty()) {
            websocketClientNotificationBO.notifyTrainingRequestReceived(pendingCohortIds);
        }
        return Optional.empty();
    }

    @Transactional
    public Long createTransactional(LocalQueryDTO query, ProjectFederatedExperimentForLocalDTO requestLearning,
                                    QueryResultWrapperDTO harmonizedQueryResult) {
        LearningRequestDTO requestLearningDTO = new LearningRequestDTO();
        requestLearningDTO.setStatus(LearningRequestStatus.PENDING);
        requestLearningDTO.setGlobalFLExperimentUniqueId(requestLearning.getGlobalUniqueId());
        LearningRequestEntity entity = mapper.dtoToEntity(requestLearningDTO);

        entity.setName(requestLearning.getName());
        entity.setDescription(requestLearning.getDescription());
        entity.setPlatformUserId(requestLearning.getKeycloakId());
        entity.setModelNeedToBePublic(requestLearning.getModelNeedToBePublic());

        ao.persist(entity);

        LearningProjectEntity project = projectBO.createForRequest(
                query == null ? null : query.getId(), entity, requestLearning.getProjectVersion(), requestLearning.getWorkflow());
        project.setPlatformIsCoordinator(requestLearning.getPlatformIsCoordinator());
        project.setExecutionMode(requestLearning.getMode());
        entity.setProject(project);

        if (query != null) {
            patientLearningBO.initPatientList(query.getId(), entity, harmonizedQueryResult);
        }
        return entity.getId();
    }

    @Transactional
    public Set<Long> initCohortDecisionsTransactional(Long id, String platformUserId, ExecutionMode mode,
                                                      int certificationLevel,
                                                      boolean needsInternetAccess) {
        LearningRequestEntity entity = ao.findByIdOptional(id)
                .orElseThrow(() -> new NotFoundException("FederatedLearningRequest not found"));
        Set<Long> cohortIds = patientLearningBO.getCohortIdsForRequests(List.of(id));
        if (cohortIds.isEmpty()) {
            cohortIds = getPatientCohortIds(entity);
        }
        Set<Long> pending = cohortDecisionBO.initForRequest(entity, cohortIds, platformUserId, mode,
                certificationLevel, needsInternetAccess);
        // cohorts without training access for the mode never take part
        Set<Long> rejected = cohortDecisionBO.getRejectedCohortIds(id);
        if (!rejected.isEmpty()) {
            patientLearningBO.removePatientsOfCohorts(id, rejected);
        }
        return pending;
    }


    public LearningRequestDTO update(Long id, LearningRequestDTO updateDTO,
                                     String keycloakId) {
        LearningRequestStatus requested = updateDTO.getStatus();
        if (requested != LearningRequestStatus.APPROVED
                && requested != LearningRequestStatus.REJECTED) {
            throw new IllegalArgumentException("A training request can only be approved or rejected");
        }

        LearningRequestEntity entity = ao.findByIdOptional(id)
                .orElseThrow(() -> new NotFoundException("FederatedLearningRequest not found"));

        if (entity.getStatus() != LearningRequestStatus.PENDING) {
            throw new IllegalArgumentException("Status has already been changed");
        }
        if (requested == LearningRequestStatus.APPROVED
                && Boolean.TRUE.equals(entity.getModelNeedToBePublic())
                && Boolean.FALSE.equals(updateDTO.getModelCanBePublic())) {
            throw new IllegalArgumentException("The model of this request needs to be public");
        }

        Set<Long> decidableCohortIds = cohortDecisionBO.getDecidableCohortIds(id, keycloakId);
        if (decidableCohortIds.isEmpty()) {
            throw new ForbiddenException("You have no pending cohort to decide on this request");
        }

        FederatedLearningRequestCohortStatus decision =
                requested == LearningRequestStatus.APPROVED
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
            long acceptedNow = countAccepted(id, decidableCohortIds);
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

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    void finalizeAutoApprovedCohortsTransactional(Long id) {
        Set<Long> autoApprovedCohortIds = cohortDecisionBO.getApprovedCohortIds(id);
        long autoAcceptedPatients = countAccepted(id, autoApprovedCohortIds);
        if (autoAcceptedPatients > 0) {
            notifyAcceptedPatients(id, autoAcceptedPatients, null);
        }
        resolveRequestStatus(id, null);
    }

    // an external database has no patients here, each approved cohort counts once
    private long countAccepted(Long id, Set<Long> cohortIds) {
        LearningProjectEntity project = ao.findById(id).getProject();
        return project != null && project.getTreSource() != null
                ? cohortIds.size() : patientLearningBO.countForRequestAndCohorts(id, cohortIds);
    }

    private void notifyAcceptedPatients(Long id, long patientCount, Boolean modelCanBePublic) {
        LearningRequestEntity entity = ao.findByIdForUpdate(id)
                .orElseThrow(() -> new NotFoundException("FederatedLearningRequest not found"));
        if (Boolean.FALSE.equals(modelCanBePublic)) {
            entity.setModelCanBePublic(false);
        }

        String globalId = entity.getGlobalExperimentUniqueId();
        int count = privacyBO.modifyQueryCount(patientCount).intValue();
        String clinicId = learningRunBO.approve(entity);
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
        LearningRequestEntity entity = ao.findByIdForUpdate(id)
                .orElseThrow(() -> new NotFoundException("FederatedLearningRequest not found"));
        entity.setModelCanBePublic(false);
    }

    private void resolveRequestStatus(Long id, Boolean modelCanBePublic) {
        LearningRequestEntity entity = ao.findByIdForUpdate(id)
                .orElseThrow(() -> new NotFoundException("FederatedLearningRequest not found"));

        if (Boolean.FALSE.equals(modelCanBePublic)) {
            entity.setModelCanBePublic(false);
        }
        if (entity.getStatus() != LearningRequestStatus.PENDING
                || cohortDecisionBO.hasPending(id)
                || cohortDecisionBO.getCohortIds(id).isEmpty()) {
            return;
        }

        long remainingPatients = patientLearningBO.countForRequest(id);
        if (!cohortDecisionBO.hasApproved(id) || remainingPatients == 0) {
            entity.setStatus(LearningRequestStatus.REJECTED);
            return;
        }

        entity.setStatus(LearningRequestStatus.APPROVED);
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
                .map(LearningRequestEntity::getGlobalExperimentUniqueId)
                .toList();
    }

    @Transactional
    public void finishLearning(String globalUniqueQueryId) {
        updateLearningStatus(globalUniqueQueryId, LearningRequestStatus.COMPLETED);
    }

    @Transactional
    public void stopLearning(String globalUniqueQueryId) {
        updateLearningStatus(globalUniqueQueryId, LearningRequestStatus.STOPPED);
    }

    private void updateLearningStatus(String globalUniqueQueryId, LearningRequestStatus status) {
        if (!ao.updateStatusTransactional(globalUniqueQueryId, status)) {
            throw new NotFoundException("FederatedLearningRequest not found");
        }
    }
}
