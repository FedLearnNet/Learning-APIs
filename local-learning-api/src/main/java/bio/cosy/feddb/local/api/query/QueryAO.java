package bio.cosy.feddb.local.api.query;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class QueryAO implements PanacheRepository<QueryEntity> {

    private static final String COHORT_PATIENT_COUNT_SQL = """
            SELECT pm.cohort_id, COUNT(*)
            FROM patient_meta pm
            JOIN (%s) matched ON matched.patient_id = pm.id
            WHERE pm.cohort_id IS NOT NULL
            GROUP BY pm.cohort_id
            """;

    @ConfigProperty(name = "feddb.query.timeout-seconds", defaultValue = "120")
    int queryTimeoutSeconds;

    public Optional<QueryEntity> findByGlobalId(String globalQueryId) {
        return find("globalQueryId = ?1", globalQueryId).firstResultOptional();
    }

    public Optional<LocalDateTime> findMostRecentQueryTimeByUserAndCohort(String keycloakId, Long cohortId) {
        return find("keycloakId = ?1 AND status <> ?2 AND ?3 MEMBER OF queriedCohortIds ORDER BY receivedAt DESC",
                keycloakId, QueryStatusEnum.REJECTED_EAM_PRE_HARMONIZED, cohortId)
                .firstResultOptional()
                .map(QueryEntity::getReceivedAt);
    }

    public List<QueryResultDTO> findPatientCountsByCohort(String patientMatchSql) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = getEntityManager()
                .createNativeQuery(COHORT_PATIENT_COUNT_SQL.formatted(patientMatchSql))
                .setHint("jakarta.persistence.query.timeout", queryTimeoutSeconds * 1000)
                .getResultList();

        return rows.stream()
                .map(row -> new QueryResultDTO(((Number) row[0]).longValue(), ((Number) row[1]).longValue()))
                .toList();
    }
}
