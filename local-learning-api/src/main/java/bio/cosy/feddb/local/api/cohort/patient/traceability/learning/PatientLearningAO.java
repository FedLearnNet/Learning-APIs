package bio.cosy.feddb.local.api.cohort.patient.traceability.learning;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import io.quarkus.panache.common.Page;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@ApplicationScoped
public class PatientLearningAO implements PanacheRepository<PatientLearningEntity> {

    private static final String INSERT_FROM_QUERY_PATIENTS_SQL = """
            INSERT INTO patient_learning (patient_id, request_id, created_at, updated_at, version)
            SELECT qp.patient_id, :requestId, now(), now(), 0
            FROM query_patient qp
            JOIN patient_meta pm ON pm.id = qp.patient_id
            JOIN (%s) matched ON matched.patient_id = qp.patient_id
            WHERE qp.query_id = :queryId
            ON CONFLICT (patient_id, request_id) DO NOTHING
            """;

    public List<PatientLearningEntity> getAllForRequest(Long requestId) {
        return find("request.id", requestId).list();
    }

    public List<PatientLearningEntity> getAllForRequestAndCohorts(Long requestId,
                                                                  Set<Long> cohortIds) {
        if (cohortIds == null || cohortIds.isEmpty()) {
            return List.of();
        }
        return find("request.id = ?1 and patient.cohort.id in ?2", requestId, cohortIds).list();
    }

    public long countForRequest(Long requestId) {
        return count("request.id", requestId);
    }

    public long countForRequestAndCohorts(Long requestId, Set<Long> cohortIds) {
        if (cohortIds == null || cohortIds.isEmpty()) {
            return 0;
        }
        return count("request.id = ?1 and patient.cohort.id in ?2", requestId, cohortIds);
    }

    public long deleteForRequestAndCohortsExcept(Long requestId, Set<Long> cohortIds,
                                                 Set<Long> keepIds) {
        if (cohortIds == null || cohortIds.isEmpty()) {
            return 0;
        }
        if (keepIds == null || keepIds.isEmpty()) {
            return deleteForRequestAndCohorts(requestId, cohortIds);
        }
        return delete("request.id = ?1 and patient.cohort.id in ?2 and id not in ?3",
                requestId, cohortIds, keepIds);
    }

    public long deleteForRequestAndCohorts(Long requestId, Set<Long> cohortIds) {
        if (cohortIds == null || cohortIds.isEmpty()) {
            return 0;
        }
        return delete("request.id = ?1 and patient.cohort.id in ?2", requestId, cohortIds);
    }

    public int insertFromQueryPatients(String patientMatchSql, Long queryId, Long requestId) {
        return getEntityManager()
                .createNativeQuery(INSERT_FROM_QUERY_PATIENTS_SQL.formatted(patientMatchSql))
                .setParameter("requestId", requestId)
                .setParameter("queryId", queryId)
                .executeUpdate();
    }

    public List<PatientLearningEntity> list(
            Page page, Long internalCohortId, Long internalPatientId, Long requestId,
            Set<Long> accessibleCohortIds) {

        if (accessibleCohortIds == null || accessibleCohortIds.isEmpty()) {
            return List.of();
        }

        StringBuilder queryBuilder = new StringBuilder();
        Map<String, Object> parameters = new HashMap<>();

        queryBuilder.append("patient.cohort.id IN :accessibleCohortIds");
        parameters.put("accessibleCohortIds", accessibleCohortIds);

        if (internalCohortId != null) {
            queryBuilder.append(" AND patient.cohort.id  = :internalCohortId");
            parameters.put("internalCohortId", internalCohortId);
        }
        if (internalPatientId != null) {
            if (!queryBuilder.isEmpty()) {
                queryBuilder.append(" AND ");
            }
            queryBuilder.append("patient.id  = :internalPatientId");
            parameters.put("internalPatientId", internalPatientId);
        }
        if (requestId != null) {
            if (!queryBuilder.isEmpty()) {
                queryBuilder.append(" AND ");
            }
            queryBuilder.append("request.id  = :requestId");
            parameters.put("requestId", requestId);
        }

        return find(queryBuilder.toString(), parameters).page(page).list();
    }
}
