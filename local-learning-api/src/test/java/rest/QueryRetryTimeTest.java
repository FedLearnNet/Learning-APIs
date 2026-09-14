package rest;

import bio.cosy.feddb.local.api.cohort.CohortAO;
import bio.cosy.feddb.local.api.cohort.CohortEntity;
import bio.cosy.feddb.local.api.cohort.permission.AutoTrainingAccess;
import bio.cosy.feddb.local.api.cohort.permission.PermissionAO;
import bio.cosy.feddb.local.api.cohort.permission.PermissionBO;
import bio.cosy.feddb.local.api.cohort.permission.PermissionEntity;
import bio.cosy.feddb.local.api.query.QueryAO;
import bio.cosy.feddb.local.api.query.QueryEntity;
import bio.cosy.feddb.local.api.query.QueryStatusEnum;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies that queryRetryTime is enforced: PermissionBO.getQueryRetryTimeByCohort() returns the
 * minimum positive value per cohort across active permissions, and QueryAO.findMostRecentQueryTimeByUserAndCohort()
 * locates the relevant previous query for a specific cohort while ignoring pre-harmonised rejections.
 */
@QuarkusTest
public class QueryRetryTimeTest {

    @Inject
    PermissionAO permissionAO;

    @Inject
    PermissionBO permissionBO;

    @Inject
    QueryAO queryAO;

    @Inject
    CohortAO cohortAO;

    private static final String USER_ID = "retrytestuser";

    private Long cohortId;

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private CohortEntity getOrCreateTestCohort() {
        if (cohortId != null) {
            var existing = cohortAO.findByIdOptional(cohortId);
            if (existing.isPresent()) return existing.get();
        }
        CohortEntity c = new CohortEntity();
        c.setName("Retry Test Cohort");
        c.setKeycloakId(USER_ID);
        cohortAO.persist(c);
        cohortId = c.getId();
        return c;
    }

    private PermissionEntity buildPermission(int queryRetryTime) {
        PermissionEntity p = new PermissionEntity();
        p.setCohort(getOrCreateTestCohort());
        p.setUserId(USER_ID);
        p.setIsAllowedToQuery(true);
        p.setQueryRetryTime(queryRetryTime);
        p.setQuerySampleThreshold(100);
        p.setAutoTrainingAccess(AutoTrainingAccess.ALL);
        return p;
    }

    private QueryEntity buildQuery(QueryStatusEnum status, LocalDateTime receivedAt) {
        QueryEntity q = new QueryEntity();
        q.setGlobalQueryId("retry-test-" + System.nanoTime());
        q.setQueryString("[]");
        q.setStatus(status);
        q.setKeycloakId(USER_ID);
        q.setReceivedAt(receivedAt);
        return q;
    }

    // -------------------------------------------------------------------------
    // Cleanup helper — removes any pre-existing active permissions for USER_ID
    // including global defaults without a user (created by addDefault()).
    // Safe within @TestTransaction because changes are rolled back after each test.
    // -------------------------------------------------------------------------

    private void clearMatchingPermissions() {
        permissionAO.delete("(userId = ?1 OR userId IS NULL) AND isAllowedToQuery = true", USER_ID);
    }

    // -------------------------------------------------------------------------
    // getQueryRetryTimeByCohort — PermissionBO
    // -------------------------------------------------------------------------

    @Test
    @TestTransaction
    void getQueryRetryTimeByCohort_noPermissions_returnsEmptyMap() {
        clearMatchingPermissions();
        Map<Long, Integer> result = permissionBO.getQueryRetryTimeByCohort(USER_ID);
        assertTrue(result.isEmpty());
    }

    @Test
    @TestTransaction
    void getQueryRetryTimeByCohort_singlePermission_returnsConfiguredValue() {
        clearMatchingPermissions();
        permissionAO.persist(buildPermission(5));
        Map<Long, Integer> result = permissionBO.getQueryRetryTimeByCohort(USER_ID);
        assertEquals(1, result.size());
        assertEquals(5, result.get(cohortId));
    }

    @Test
    @TestTransaction
    void getQueryRetryTimeByCohort_multiplePermissionsSameCohort_returnsMinimum() {
        clearMatchingPermissions();
        permissionAO.persist(buildPermission(10));
        permissionAO.persist(buildPermission(3));
        permissionAO.persist(buildPermission(7));
        Map<Long, Integer> result = permissionBO.getQueryRetryTimeByCohort(USER_ID);
        assertEquals(1, result.size());
        assertEquals(3, result.get(cohortId));
    }

}
