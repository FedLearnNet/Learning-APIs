package bio.cosy.feddb.local.api.cohort.patient.traceability.query;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import io.quarkus.panache.common.Page;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class QueryPatientAO implements PanacheRepository<QueryPatientEntity> {

    private static final String INSERT_MATCHED_PATIENTS_SQL = """
            INSERT INTO query_patient (patient_id, query_id, created_at, updated_at, version)
            SELECT pm.id, :queryId, now(), now(), 0
            FROM patient_meta pm
            JOIN (%s) matched ON matched.patient_id = pm.id
            WHERE pm.cohort_id IN (:cohortIds)
            ON CONFLICT (patient_id, query_id) DO NOTHING
            """;

    public List<QueryPatientEntity> getAllForQuery(Long queryId) {
        return find("query.id", queryId).list();
    }

    public int insertMatchedPatients(String patientMatchSql, Long queryId, Collection<Long> cohortIds) {
        if (cohortIds == null || cohortIds.isEmpty()) {
            return 0;
        }
        return getEntityManager()
                .createNativeQuery(INSERT_MATCHED_PATIENTS_SQL.formatted(patientMatchSql))
                .setParameter("queryId", queryId)
                .setParameter("cohortIds", cohortIds)
                .executeUpdate();
    }

    public List<QueryPatientEntity> list(
            Page page, Long internalCohortId, Long internalPatientId, Long queryId) {
        // Build the query dynamically, adding conditions and an AND operator as needed
        // Basically creates
        // "patient.cohort.Id = :internalCohortId AND patient.Id = :internalPatientId AND query.id = :queryId"
        // Doesnt add the relevant conditions/ands if not provided
        StringBuilder queryBuilder = new StringBuilder();
        Map<String, Object> parameters = new HashMap<>();

        if (internalCohortId != null) {
            queryBuilder.append("patient.cohort.Id  = :internalCohortId");
            parameters.put("internalCohortId", internalCohortId);
        }
        if (internalPatientId != null) {
            if (!queryBuilder.isEmpty()) {
                queryBuilder.append(" AND ");
            }
            queryBuilder.append("patient.Id  = :internalPatientId");
            parameters.put("internalPatientId", internalPatientId);
        }
        if (queryId != null) {
            if (!queryBuilder.isEmpty()) {
                queryBuilder.append(" AND ");
            }
            queryBuilder.append("query.id  = :queryId");
            parameters.put("queryId", queryId);
        }

        return find(queryBuilder.toString(), parameters).page(page).list();
    }
}
