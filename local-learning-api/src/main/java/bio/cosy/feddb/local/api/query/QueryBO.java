package bio.cosy.feddb.local.api.query;

import bio.cosy.feddb.core.api.query.QueryClientResponseDTO;
import bio.cosy.feddb.core.api.query.QueryDTO;
import bio.cosy.feddb.core.base.BaseBo;
import bio.cosy.feddb.local.api.cohort.member.CohortMemberAuthBO;
import bio.cosy.feddb.local.api.cohort.patient.traceability.query.QueryPatientBO;
import bio.cosy.feddb.local.api.cohort.permission.PermissionBO;
import bio.cosy.feddb.local.api.cohort.permission.PermissionDTO;
import bio.cosy.feddb.local.api.cohort.queryability.CohortQueryAbilityBO;
import bio.cosy.feddb.local.api.privacy.PrivacyBO;
import bio.cosy.feddb.local.api.schema.LocalSchemaNodeDTO;
import bio.cosy.feddb.local.api.schema.schemanode.SchemaNodeEntity;
import bio.cosy.feddb.local.api.schema.schemanode.SchemaNodeMapper;
import bio.cosy.feddb.local.api.search.SearchResultDTO;
import bio.cosy.feddb.local.api.search.SearchResultType;
import bio.cosy.feddb.local.api.search.SearchScoreUtil;
import bio.cosy.feddb.local.helper.QuarkusModeService;
import io.quarkus.arc.log.LoggerName;
import io.quarkus.logging.Log;
import io.quarkus.narayana.jta.runtime.TransactionConfiguration;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotAllowedException;
import jakarta.ws.rs.NotFoundException;
import org.jboss.logging.Logger;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@ApplicationScoped
public class QueryBO extends BaseBo<LocalQueryDTO, QueryEntity, QueryAO, QueryMapper> {

    @LoggerName("QueryBO")
    Logger logger;

    @Inject
    PermissionBO permissionBO;

    @Inject
    CohortMemberAuthBO cohortMemberAuthBO;

    @Inject
    QueryPatientBO queryPatientBO;

    @Inject
    QueryBuilderBO queryBuilderBO;

    @Inject
    QueryBuilderBO builderBO;

    @Inject
    SchemaNodeMapper schemaNodeMapper;

    @Inject
    PrivacyBO privacyBO;

    @Inject
    CohortQueryAbilityBO cohortQueryAbilityBO;

    public LocalQueryDTO findById(Long id) {
        Optional<QueryEntity> entityOptional = ao.findByIdOptional(id);
        if (entityOptional.isEmpty()) {
            throw new NotFoundException("QueryEntity not found");
        }
        LocalQueryDTO query = mapper.entityToDto(entityOptional.get());
        if (query == null) {
            throw new NotFoundException("QueryDTO not found for ID: " + id);
        }
        return enhanceQuery(query);
    }

    public Set<Long> getCohortIds(Long id) {
        QueryEntity entity = ao.findByIdOptional(id)
                .orElseThrow(() -> new NotFoundException("QueryEntity not found"));
        if (entity.getPatients() == null) {
            return Set.of();
        }
        return entity.getPatients().stream()
                .map(queryPatient -> queryPatient.getPatient().getCohort().getId())
                .collect(Collectors.toSet());
    }

    @Override
    public List<LocalQueryDTO> getAll() {
        List<LocalQueryDTO> queries = super.getAll();
        return queries.stream()
                .map(this::enhanceQuery)
                .toList();
    }

    public List<SearchResultDTO<LocalQueryDTO>> search(String query, String keycloakId) {
        return ao.listAll().stream()
                .filter(entity -> hasCohortSearchAccess(getCohortIds(entity), keycloakId))
                .map(entity -> {
                    LocalQueryDTO dto = mapper.entityToDto(entity);
                    String title = "Query " + dto.getGlobalQueryId();
                    int score = SearchScoreUtil.score(query, title, dto.getStatusMessage(),
                            String.valueOf(dto.getStatus()));
                    return SearchScoreUtil.toResult(SearchResultType.QUERY, dto, title, score);
                })
                .filter(result -> result.getScore() > 0)
                .toList();
    }

    private Set<Long> getCohortIds(QueryEntity entity) {
        if (entity.getPatients() == null) {
            return Set.of();
        }
        return entity.getPatients().stream()
                .map(queryPatient -> queryPatient.getPatient().getCohort().getId())
                .collect(Collectors.toSet());
    }

    private boolean hasCohortSearchAccess(Set<Long> cohortIds, String keycloakId) {
        return cohortIds != null && !cohortIds.isEmpty() && cohortIds.stream()
                .anyMatch(cohortId -> cohortMemberAuthBO.isMember(cohortId, keycloakId));
    }

    private LocalQueryDTO enhanceQuery(LocalQueryDTO query) {
        // Enhance the query with additional information if needed
        // This is a placeholder for any future enhancements

        List<LocalQueryItemDTO> enhanced = query.getQuery().stream()
                .map(q -> {
                    List<SchemaNodeEntity> schemaNodeEntities = builderBO.getSchemaNodesForQueryItem(q.getOntologyId(), q.getDataTypeId());
                    List<LocalSchemaNodeDTO> schemaNodes = schemaNodeMapper.entitiesToDtos(schemaNodeEntities);
                    return mapper.localQueryEnhanced(q, schemaNodes);
                }).toList();

        query.setEnhancedQuery(enhanced);

        return query;
    }

    @Transactional
    public Optional<LocalQueryDTO> findByGlobalIdOptionalTransactional(String globalQueryId) {
        return ao.findByGlobalId(globalQueryId).map(mapper::entityToDto);
    }

    @Transactional
    public Optional<Integer> findCountByGlobalIdOptionalTransactional(String globalQueryId) {
        return ao.findByGlobalId(globalQueryId).map(e -> e.getPatients().size());
    }


    public QueryClientResponseDTO handleQuery(QueryDTO query) {
        logger.infof("Processing query: %s", query);
        String queryGlobalUniqueId = query.getGlobalUniqueId();

        //Check if query with this ID already exists and if so return 0 for security reasons
        Optional<Integer> foundQueryCount = findCountByGlobalIdOptionalTransactional(queryGlobalUniqueId);
        if (foundQueryCount.isPresent()) {
            Integer count = foundQueryCount.get();
            if (QuarkusModeService.isDevMode()) {
                //TODO check if there is a better way for local development e.g. regen. for fire the ID or so
                logger.infof("Query with ID %s already processed, returning cached count %s (dev/staging)", queryGlobalUniqueId, count);
                return QueryClientResponseDTO.createResponse(count.longValue(), queryGlobalUniqueId);
            }
            logger.infof("Query with ID %s already processed, returning 0", queryGlobalUniqueId);
            return QueryClientResponseDTO.createResponse(0L, queryGlobalUniqueId);
        }


        //TODO GROUP_ID
        List<PermissionDTO> allowedCohorts = permissionBO.getAllowedToQueryCohortIds(query.getKeycloakId());
        List<Long> allowedCohortIds = allowedCohorts.stream()
                .map(PermissionDTO::getCohortId)
                .toList();
        if (allowedCohorts.isEmpty()) {
            String message = "User " + query.getKeycloakId() + " is not allowed to query";
            logger.warn(message);
            createTransactional(query, message, QueryStatusEnum.REJECTED_EAM_PRE_HARMONIZED);
            return QueryClientResponseDTO.createErrorResponse("Permission denied", queryGlobalUniqueId);
        }
        allowedCohortIds = cohortQueryAbilityBO.filterQueryableCohortIds(query.getQuery(), allowedCohortIds);

        if (allowedCohorts.isEmpty()) {
            String message = "Cohort QueryAbility is rejecting the query";
            logger.warn(message);
            createTransactional(query, message, QueryStatusEnum.REJECTED_EAM_PRE_HARMONIZED);
            return QueryClientResponseDTO.createErrorResponse("Permission denied", queryGlobalUniqueId);
        }


        Map<Long, Integer> retryTimeByCohort = permissionBO.getQueryRetryTimeByCohort(query.getKeycloakId());
        if (!retryTimeByCohort.isEmpty()) {
            LocalDateTime now = LocalDateTime.now();
            List<Long> blocked = retryTimeByCohort.entrySet().stream()
                    .filter(e -> {
                        Optional<LocalDateTime> last = ao.findMostRecentQueryTimeByUserAndCohort(query.getKeycloakId(), e.getKey());
                        return last.isPresent() && now.isBefore(last.get().plusSeconds(e.getValue()));
                    })
                    .map(Map.Entry::getKey)
                    .toList();
            if (!blocked.isEmpty()) {
                allowedCohortIds = allowedCohortIds.stream().filter(id -> !blocked.contains(id)).toList();
                if (allowedCohortIds.isEmpty()) {
                    String message = "Query retry time not elapsed for all permitted cohorts. Please wait before retrying.";
                    logger.warn(message);
                    createTransactional(query, message, QueryStatusEnum.REJECTED_EAM_PRE_HARMONIZED);
                    return QueryClientResponseDTO.createErrorResponse(message, queryGlobalUniqueId);
                }
                logger.infof("Cohorts %s excluded from query due to retry time not elapsed", blocked);
            }
        }

        final List<Long> resolvedCohortIds = allowedCohortIds;
        LocalQueryDTO createdLocalQuery = createTransactional(query, "Query received by the EAM", QueryStatusEnum.RECEIVED_EAM);
        Long createdQueryId = createdLocalQuery.getId();
        QueryResultWrapperDTO harmonizedQueryResult;
        try {
            harmonizedQueryResult = runQuery(query, resolvedCohortIds);
        } catch (Exception e) {
            createdLocalQuery.setQueriedCohortIds(new HashSet<>(resolvedCohortIds));
            createdLocalQuery.setStatus(QueryStatusEnum.REJECTED_HARMONIZED);
            createdLocalQuery.setStatusMessage(e.getMessage());
            updateTransactional(createdQueryId, createdLocalQuery);
            return QueryClientResponseDTO.createErrorResponse(e.getMessage(), queryGlobalUniqueId);
        }
        logger.info("Received query result: " + harmonizedQueryResult);

        long rawCount = Optional.ofNullable(harmonizedQueryResult.getResult())
                .map(results -> results.stream()
                        .filter(resultDTO -> {
                            Long cohortId = resultDTO.getCohortId();
                            Integer threshold = allowedCohorts.stream()
                                    .filter(a -> a.getCohortId().equals(cohortId))
                                    .findFirst()
                                    .map(PermissionDTO::getQuerySampleThreshold)
                                    .orElse(0);
                            return resultDTO.getPatientCount() >= threshold;
                        })
                        .mapToLong(QueryResultDTO::getPatientCount)
                        .sum())
                .orElse(0L);
        Long count = privacyBO.modifyQueryCount(rawCount);

        logger.infof("[QUERY-EVAL] query=%s cohorts=%s raw_count=%d released_count=%d suppressed=%b",
                queryGlobalUniqueId, resolvedCohortIds, rawCount, count,
                rawCount > 0 && count == 0);

        if (count == 0) {
            logger.warn("Query was rejected by privacy measures");
            createdLocalQuery.setQueriedCohortIds(new HashSet<>(resolvedCohortIds));
            createdLocalQuery.setStatus(QueryStatusEnum.REJECTED_EAM_POST_HARMONIZED);
            createdLocalQuery.setStatusMessage("Query was rejected by privacy measures");
            updateTransactional(createdQueryId, createdLocalQuery);
            return QueryClientResponseDTO.createErrorResponse(createdLocalQuery.getStatusMessage(), queryGlobalUniqueId);
        }

        createdLocalQuery.setQueriedCohortIds(new HashSet<>(resolvedCohortIds));
        createdLocalQuery.setStatus(QueryStatusEnum.COMPLETED);
        createdLocalQuery.setStatusMessage("Completed");
        updateTransactional(createdQueryId, createdLocalQuery);
        final String patientMatchSql = harmonizedQueryResult.getPatientMatchSql();
        Log.infof("Storing query results for query %s", createdQueryId);
        Infrastructure.getDefaultWorkerPool().execute(() ->
                queryPatientBO.storeMatchedPatientsTransactional(patientMatchSql, resolvedCohortIds, createdQueryId));
        return QueryClientResponseDTO.createResponse(count, queryGlobalUniqueId);
    }


    @Transactional
    public LocalQueryDTO createTransactional(QueryDTO query, String message, QueryStatusEnum status) {
        LocalQueryDTO localQueryDTO = new LocalQueryDTO();
        localQueryDTO.setGlobalQueryId(query.getGlobalUniqueId());
        localQueryDTO.setKeycloakId(query.getKeycloakId());
        localQueryDTO.setQuery(query.getQuery());
        localQueryDTO.setStatusMessage(message);
        localQueryDTO.setStatus(status);
        localQueryDTO.setReceivedAt(LocalDateTime.now());
        return create(localQueryDTO);
    }

    @Transactional
    public LocalQueryDTO updateTransactional(Long id, LocalQueryDTO query) {
        if (!query.getId().equals(id)) {
            throw new NotAllowedException(String.format("ID %s not matching with the object", id));
        }
        return update(query);
    }

    public QueryResultWrapperDTO runQuery(LocalQueryDTO query, String keycloakId) {
        //TODO GROUP_ID
        List<PermissionDTO> allowedCohorts = permissionBO.getAllowedToQueryCohortIds(keycloakId);
        List<Long> allowedCohortIds = allowedCohorts.stream()
                .map(PermissionDTO::getCohortId)
                .toList();
        if (allowedCohorts.isEmpty()) {
            String message = "User " + keycloakId + " is not allowed to query";
            logger.warn(message);
        }

        allowedCohortIds = cohortQueryAbilityBO.filterQueryableCohortIds(query.getQuery(), allowedCohortIds);
        if (allowedCohorts.isEmpty()) {
            String message = "Cohort QueryAbility is rejecting the query";
            logger.warn(message);
        }

        QueryDTO queryDTO = new QueryDTO();
        queryDTO.setGlobalUniqueId(query.getGlobalQueryId());
        queryDTO.setKeycloakId(keycloakId);
        queryDTO.setQuery(query.getQuery());
        try {
            return runQuery(queryDTO, allowedCohortIds);
        } catch (QueryRejectedException e) {
            logger.warnf("Query cannot be evaluated on this node; returning 0 matches: %s", e.getMessage());
            QueryResultWrapperDTO emptyResult = new QueryResultWrapperDTO();
            emptyResult.setQueryId(query.getId());
            emptyResult.setResult(List.of());
            return emptyResult;
        } catch (Exception e) {
            logger.error("Error while running query: " + e.getMessage(), e);
            return null;
        }
    }


    @Transactional(Transactional.TxType.REQUIRES_NEW)
    @TransactionConfiguration(timeout = 3600)
    public QueryResultWrapperDTO runQuery(QueryDTO query, List<Long> allowedCohortIds) {
        String patientMatchSql = queryBuilderBO.buildCountDistinctSql(query);

        QueryResultWrapperDTO queryResult = new QueryResultWrapperDTO();
        queryResult.setQueryId(query.getId());
        queryResult.setPatientMatchSql(patientMatchSql);

        List<QueryResultDTO> countsByCohort = ao.findPatientCountsByCohort(patientMatchSql);
        List<QueryResultDTO> allowedResults = countsByCohort.stream()
                .filter(result -> {
                    boolean isAllowed = allowedCohortIds.contains(result.getCohortId());
                    if (!isAllowed) {
                        logger.errorf("Cohort %s is not allowed but was returned by query.", result.getCohortId());
                    }
                    return isAllowed;
                })
                .toList();

        queryResult.setResult(allowedResults);
        logger.debugf("Executed patient match SQL: %s", patientMatchSql);
        logger.infof("Query executed successfully: %s", queryResult);
        return queryResult;
    }

}
