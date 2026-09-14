package bio.cosy.feddb.local.api.cohort.patient.traceability.learning;

import bio.cosy.feddb.core.base.BaseBo;
import bio.cosy.feddb.core.base.PagedResponse;
import bio.cosy.feddb.local.api.learning.request.FederatedLearningRequestBO;
import bio.cosy.feddb.local.api.learning.request.FederatedLearningRequestEntity;
import bio.cosy.feddb.local.api.query.QueryResultWrapperDTO;
import io.quarkus.logging.Log;
import io.quarkus.panache.common.Page;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.ForbiddenException;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@ApplicationScoped
public class PatientLearningBO extends BaseBo<PatientLearningDTO, PatientLearningEntity, PatientLearningAO, PatientLearningMapper> {

    @Inject
    FederatedLearningRequestBO requestBO;

    public PagedResponse<PatientLearningDTO> list(
            Page page, Long internalCohortId, Long internalPatientId, Long requestId,
            Set<Long> accessibleCohortIds) {

        List<PatientLearningEntity> foundEntity = ao.list(page, internalCohortId,
                internalPatientId, requestId, accessibleCohortIds);
        List<PatientLearningDTO> found = mapper.entitiesToDtos(foundEntity);
        return new PagedResponse<>(found, page.index, page.size);
    }

    public void initPatientList(Long queryId, FederatedLearningRequestEntity request, QueryResultWrapperDTO requeried) {
        String patientMatchSql = requeried == null ? null : requeried.getPatientMatchSql();
        if (patientMatchSql == null || patientMatchSql.isBlank()) {
            Log.warn("No patient match SQL for query " + queryId + ", learning patient list stays empty.");
            return;
        }

        ao.flush();
        int created = ao.insertFromQueryPatients(patientMatchSql, queryId, request.getId());
        Log.infof("Initialised learning patient list with %d patients for request %s", created, request.getId());
    }

    public void acceptPatients(List<PatientLearningDTO> update, Set<Long> editableCohortIds) {
        List<Long> requestIds = update.stream()
                .map(PatientLearningDTO::getRequestId)
                .distinct()
                .toList();

        requestBO.listExists(requestIds);

        Map<Long, Set<Long>> acceptedPatientsByRequest = update.stream()
                .collect(Collectors.groupingBy(
                        PatientLearningDTO::getRequestId,
                        Collectors.mapping(PatientLearningDTO::getId, Collectors.toSet())
                ));

        for (Long requestId : requestIds) {
            retainPatients(requestId, editableCohortIds,
                    acceptedPatientsByRequest.get(requestId));
        }
    }

    /**
     * Keeps only the given patient links inside the caller's cohorts. Patients of cohorts the
     * caller does not own stay untouched, so one owner cannot drop another owner's contribution.
     *
     * @return how many links were removed
     */
    public long retainPatients(Long requestId, Set<Long> cohortIds, Set<Long> keepIds) {
        Set<Long> validIds = ao.getAllForRequestAndCohorts(requestId, cohortIds).stream()
                .map(PatientLearningEntity::getId)
                .collect(Collectors.toSet());
        if (keepIds == null || keepIds.contains(null) || !validIds.containsAll(keepIds)) {
            throw new ForbiddenException(
                    "Selected patients do not belong to this request and the caller's cohorts");
        }
        return ao.deleteForRequestAndCohortsExcept(requestId, cohortIds, keepIds);
    }

    public long countForRequest(Long requestId) {
        return ao.countForRequest(requestId);
    }

    public long countForRequestAndCohorts(Long requestId, Set<Long> cohortIds) {
        return ao.countForRequestAndCohorts(requestId, cohortIds);
    }

    public long removePatientsOfCohorts(Long requestId, Set<Long> cohortIds) {
        return ao.deleteForRequestAndCohorts(requestId, cohortIds);
    }

    public Set<Long> getCohortIdsForRequests(List<Long> requestIds) {
        if (requestIds == null || requestIds.isEmpty()) {
            return Set.of();
        }

        return requestIds.stream()
                .distinct()
                .flatMap(requestId -> ao.getAllForRequest(requestId).stream())
                .map(entity -> entity.getPatient().getCohort().getId())
                .collect(Collectors.toSet());
    }

}
