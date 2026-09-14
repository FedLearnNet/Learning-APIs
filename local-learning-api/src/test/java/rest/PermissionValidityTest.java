package rest;

import bio.cosy.feddb.local.api.cohort.CohortAO;
import bio.cosy.feddb.local.api.cohort.permission.AutoTrainingAccess;
import bio.cosy.feddb.local.api.cohort.permission.PermissionAO;
import bio.cosy.feddb.local.api.cohort.permission.PermissionBO;
import bio.cosy.feddb.local.api.cohort.permission.PermissionEntity;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the validFrom / validUntil date-window enforcement in PermissionAO.isAllowedToQuery().
 * Cohort id=1 is guaranteed to exist in the test seed data (cohort.sql).
 * @TestTransaction rolls back every insert after each test completes.
 */
@QuarkusTest
public class PermissionValidityTest {

    @Inject
    PermissionAO permissionAO;

    @Inject
    PermissionBO permissionBO;

    @Inject
    CohortAO cohortAO;

    private static final Long COHORT_ID = 1L;
    private static final String USER_ID = "testuser";

    // -------------------------------------------------------------------------
    // Helper
    // -------------------------------------------------------------------------

    private PermissionEntity buildPermission(LocalDate validFrom, LocalDate validUntil) {
        PermissionEntity p = new PermissionEntity();
        p.setCohort(cohortAO.findByIdOptional(COHORT_ID).orElseThrow());
        p.setUserId(USER_ID);
        p.setIsAllowedToQuery(true);
        p.setQueryRetryTime(3);
        p.setQuerySampleThreshold(100);
        p.setAutoTrainingAccess(AutoTrainingAccess.ALL);
        p.setValidFrom(validFrom);
        p.setValidUntil(validUntil);
        return p;
    }

    private boolean isActive() {
        return permissionAO.isAllowedToQuery(USER_ID)
                .stream().anyMatch(p -> USER_ID.equals(p.getUserId()));
    }

    // -------------------------------------------------------------------------
    // validFrom cases
    // -------------------------------------------------------------------------

    @Test
    @TestTransaction
    void nullValidFrom_nullValidUntil_permissionIsActive() {
        permissionAO.persist(buildPermission(null, null));
        assertTrue(isActive());
    }

    @Test
    @TestTransaction
    void pastValidFrom_nullValidUntil_permissionIsActive() {
        permissionAO.persist(buildPermission(LocalDate.now().minusDays(1), null));
        assertTrue(isActive());
    }

    @Test
    @TestTransaction
    void todayValidFrom_nullValidUntil_permissionIsActive() {
        permissionAO.persist(buildPermission(LocalDate.now(), null));
        assertTrue(isActive());
    }

    @Test
    @TestTransaction
    void futureValidFrom_nullValidUntil_permissionIsInactive() {
        permissionAO.persist(buildPermission(LocalDate.now().plusDays(1), null));
        assertFalse(isActive());
    }

    // -------------------------------------------------------------------------
    // validUntil cases
    // -------------------------------------------------------------------------

    @Test
    @TestTransaction
    void nullValidFrom_futureValidUntil_permissionIsActive() {
        permissionAO.persist(buildPermission(null, LocalDate.now().plusDays(1)));
        assertTrue(isActive());
    }

    @Test
    @TestTransaction
    void nullValidFrom_todayValidUntil_permissionIsActive() {
        permissionAO.persist(buildPermission(null, LocalDate.now()));
        assertTrue(isActive());
    }

    @Test
    @TestTransaction
    void nullValidFrom_pastValidUntil_permissionIsInactive() {
        permissionAO.persist(buildPermission(null, LocalDate.now().minusDays(1)));
        assertFalse(isActive());
    }

    // -------------------------------------------------------------------------
    // Combined range cases
    // -------------------------------------------------------------------------

    @Test
    @TestTransaction
    void pastValidFrom_futureValidUntil_permissionIsActive() {
        permissionAO.persist(buildPermission(LocalDate.now().minusDays(7), LocalDate.now().plusDays(7)));
        assertTrue(isActive());
    }

    @Test
    @TestTransaction
    void futureValidFrom_futureValidUntil_permissionIsInactive() {
        permissionAO.persist(buildPermission(LocalDate.now().plusDays(1), LocalDate.now().plusDays(7)));
        assertFalse(isActive());
    }

    @Test
    @TestTransaction
    void pastValidFrom_pastValidUntil_permissionIsInactive() {
        permissionAO.persist(buildPermission(LocalDate.now().minusDays(7), LocalDate.now().minusDays(1)));
        assertFalse(isActive());
    }
}
