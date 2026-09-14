package rest;

import bio.cosy.feddb.local.api.cohort.CohortAO;
import bio.cosy.feddb.local.api.cohort.permission.*;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests PermissionBO.hasAutoTrainingAccess() and hasAutoStatisticsAccess() enforcement.
 */
@QuarkusTest
public class PermissionAutoAccessTest {

    @Inject
    PermissionAO permissionAO;

    @Inject
    PermissionBO permissionBO;

    @Inject
    CohortAO cohortAO;

    private static final Long COHORT_ID = 1L;
    private static final Long OTHER_COHORT_ID = 2L;
    private static final String USER_ID = "accesstestuser";

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private PermissionEntity buildPermission(AutoTrainingAccess training, AutoStatisticsAccess statistics) {
        return buildPermission(COHORT_ID, training, statistics);
    }

    private PermissionEntity buildPermission(Long cohortId, AutoTrainingAccess training,
                                             AutoStatisticsAccess statistics) {
        PermissionEntity p = new PermissionEntity();
        p.setCohort(cohortAO.findByIdOptional(cohortId).orElseThrow());
        p.setUserId(USER_ID);
        p.setIsAllowedToQuery(true);
        p.setQueryRetryTime(3);
        p.setQuerySampleThreshold(100);
        p.setAutoTrainingAccess(training);
        p.setAutoStatisticsAccess(statistics);
        return p;
    }

    private boolean canTrain(int certificationLevel) {
        return permissionBO.hasAutoTrainingAccess(USER_ID, Set.of(COHORT_ID), certificationLevel, false);
    }

    private boolean canViewStatistics() {
        return permissionBO.hasAutoStatisticsAccess(USER_ID, COHORT_ID);
    }

    // -------------------------------------------------------------------------
    // hasAutoTrainingAccess — empty cohort set
    // -------------------------------------------------------------------------

    @Test
    void hasAutoTrainingAccess_emptyCohortSet_returnsTrue() {
        assertTrue(permissionBO.hasAutoTrainingAccess(USER_ID, Set.of(), 0, false));
    }

    // -------------------------------------------------------------------------
    // hasAutoTrainingAccess — AutoTrainingAccess values
    // -------------------------------------------------------------------------

    @Test
    @TestTransaction
    void hasAutoTrainingAccess_accessNull_returnsFalse() {
        permissionAO.persist(buildPermission(null, null));
        assertFalse(canTrain(0));
    }

    @Test
    @TestTransaction
    void hasAutoTrainingAccess_accessAll_returnsTrue() {
        permissionAO.persist(buildPermission(AutoTrainingAccess.ALL, null));
        assertTrue(canTrain(0));
    }

    @Test
    @TestTransaction
    void hasAutoTrainingAccess_accessNone_returnsFalse() {
        permissionAO.persist(buildPermission(AutoTrainingAccess.NONE, null));
        assertFalse(canTrain(0));
    }

    @Test
    @TestTransaction
    void hasAutoTrainingAccess_certifiedApps_uncertifiedUser_returnsFalse() {
        permissionAO.persist(buildPermission(AutoTrainingAccess.CERTIFIED_APPS, null));
        assertFalse(canTrain(0));
    }

    @Test
    @TestTransaction
    void hasAutoTrainingAccess_certifiedApps_certifiedUser_returnsTrue() {
        permissionAO.persist(buildPermission(AutoTrainingAccess.CERTIFIED_APPS, null));
        assertTrue(canTrain(1));
    }

    @Test
    @TestTransaction
    void hasAutoTrainingAccess_noMatchingPermissions_returnsFalse() {
        // Persist a permission for a different user — should not match
        PermissionEntity p = buildPermission(AutoTrainingAccess.ALL, null);
        p.setUserId("otheruser");
        permissionAO.persist(p);
        assertFalse(canTrain(0));
    }

    @Test
    @TestTransaction
    void hasAutoTrainingAccess_multiplePermissionsOnSameCohort_anyAllowsAccess() {
        // One NONE and one ALL on the same cohort — the cohort counts as granted
        permissionAO.persist(buildPermission(AutoTrainingAccess.NONE, null));
        permissionAO.persist(buildPermission(AutoTrainingAccess.ALL, null));
        assertTrue(canTrain(0));
    }

    // -------------------------------------------------------------------------
    // hasAutoTrainingAccess — several cohorts matched by one request
    // -------------------------------------------------------------------------

    @Test
    @TestTransaction
    void hasAutoTrainingAccess_allCohortsAllowed_returnsTrue() {
        permissionAO.persist(buildPermission(COHORT_ID, AutoTrainingAccess.ALL, null));
        permissionAO.persist(buildPermission(OTHER_COHORT_ID, AutoTrainingAccess.ALL, null));
        assertTrue(permissionBO.hasAutoTrainingAccess(USER_ID, Set.of(COHORT_ID, OTHER_COHORT_ID), 0, false));
    }

    @Test
    @TestTransaction
    void hasAutoTrainingAccess_oneCohortDenies_returnsFalse() {
        // Only one of the two matched cohorts has auto-training on — the request must stay manual
        permissionAO.persist(buildPermission(COHORT_ID, AutoTrainingAccess.ALL, null));
        permissionAO.persist(buildPermission(OTHER_COHORT_ID, AutoTrainingAccess.NONE, null));
        assertFalse(permissionBO.hasAutoTrainingAccess(USER_ID, Set.of(COHORT_ID, OTHER_COHORT_ID), 0, false));
    }

    @Test
    @TestTransaction
    void hasAutoTrainingAccess_oneCohortWithoutPermission_returnsFalse() {
        // A cohort the user has no permission on at all must never be auto-approved
        permissionAO.persist(buildPermission(COHORT_ID, AutoTrainingAccess.ALL, null));
        assertFalse(permissionBO.hasAutoTrainingAccess(USER_ID, Set.of(COHORT_ID, OTHER_COHORT_ID), 0, false));
    }

    @Test
    @TestTransaction
    void hasAutoTrainingAccess_certifiedAppsOnOneCohort_uncertifiedApp_returnsFalse() {
        permissionAO.persist(buildPermission(COHORT_ID, AutoTrainingAccess.ALL, null));
        permissionAO.persist(buildPermission(OTHER_COHORT_ID, AutoTrainingAccess.CERTIFIED_APPS, null));
        assertFalse(permissionBO.hasAutoTrainingAccess(USER_ID, Set.of(COHORT_ID, OTHER_COHORT_ID), 0, false));
        assertTrue(permissionBO.hasAutoTrainingAccess(USER_ID, Set.of(COHORT_ID, OTHER_COHORT_ID), 1, false));
    }

    @Test
    @TestTransaction
    void hasAutoTrainingAccess_needsInternetAccess_returnsFalse() {
        permissionAO.persist(buildPermission(COHORT_ID, AutoTrainingAccess.ALL, null));
        permissionAO.persist(buildPermission(OTHER_COHORT_ID, AutoTrainingAccess.ALL, null));
        assertFalse(permissionBO.hasAutoTrainingAccess(USER_ID, Set.of(COHORT_ID, OTHER_COHORT_ID), 0, true));
    }

    // -------------------------------------------------------------------------
    // hasAutoStatisticsAccess — empty cohort set
    // -------------------------------------------------------------------------

    @Test
    void hasAutoStatisticsAccess_emptyCohortSet_returnsTrue() {
        assertFalse(permissionBO.hasAutoStatisticsAccess(USER_ID, null));
    }

    // -------------------------------------------------------------------------
    // hasAutoStatisticsAccess — AutoStatisticsAccess values
    // -------------------------------------------------------------------------

    @Test
    @TestTransaction
    void hasAutoStatisticsAccess_accessNull_returnsFalse() {
        permissionAO.persist(buildPermission(null, null));
        assertFalse(canViewStatistics());
    }

    @Test
    @TestTransaction
    void hasAutoStatisticsAccess_accessAll_returnsTrue() {
        permissionAO.persist(buildPermission(null, AutoStatisticsAccess.ALL));
        assertTrue(canViewStatistics());
    }

    @Test
    @TestTransaction
    void hasAutoStatisticsAccess_accessNone_returnsFalse() {
        permissionAO.persist(buildPermission(null, AutoStatisticsAccess.NONE));
        assertFalse(canViewStatistics());
    }

    @Test
    @TestTransaction
    void hasAutoStatisticsAccess_multiplePermissions_anyAllowsAccess() {
        // One NONE and one ALL — anyMatch should find the ALL
        permissionAO.persist(buildPermission(null, AutoStatisticsAccess.NONE));
        permissionAO.persist(buildPermission(null, AutoStatisticsAccess.ALL));
        assertTrue(canViewStatistics());
    }
}
