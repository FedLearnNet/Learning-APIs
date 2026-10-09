package de.unihamburg.daibetes.api.project.experiment.federated.step;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The relay state queries are plain strings, so run them once against the real schema.
 */
@QuarkusTest
class ProjectFederatedExperimentStepAORelayQueriesTest {

    private static final long UNKNOWN_ID = -1L;

    @Inject
    ProjectFederatedExperimentStepAO ao;

    @Test
    void relayQueriesAreValid() {
        assertTrue(ao.findWithOpenRelayRun(UNKNOWN_ID).isEmpty());
        assertFalse(ao.setRelayRunTransactional(UNKNOWN_ID, "channel", "relayKey", Map.of("clinic-a", "client-a")));
        assertFalse(ao.setRelayStoppedTransactional(UNKNOWN_ID));
    }
}
