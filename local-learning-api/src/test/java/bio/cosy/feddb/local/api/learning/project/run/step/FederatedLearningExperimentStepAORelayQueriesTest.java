package bio.cosy.feddb.local.api.learning.project.run.step;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The relay state queries are plain strings, so run them once against the real schema.
 */
@QuarkusTest
class FederatedLearningExperimentStepAORelayQueriesTest {

    private static final long UNKNOWN_ID = -1L;

    @Inject
    FederatedLearningExperimentStepAO ao;

    @Test
    void relayQueriesAreValid() {
        assertNotNull(ao.findPendingRelayCerts());
        assertTrue(ao.findWithOpenControllerRun(UNKNOWN_ID).isEmpty());
        assertFalse(ao.startRelayCertRequestTransactional(UNKNOWN_ID, "csr"));
        assertFalse(ao.retryRelayCertRequestTransactional(UNKNOWN_ID));
        assertFalse(ao.claimRelayCertSignedTransactional(UNKNOWN_ID));
        assertFalse(ao.setRelayStoppedTransactional(UNKNOWN_ID));
    }
}
