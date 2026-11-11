package bio.cosy.feddb.core.rest;

import bio.cosy.feddb.core.api.project.ProjectStatus;
import de.unihamburg.daibetes.execution.federation.ExecutionParticipantEntity;
import de.unihamburg.daibetes.execution.run.ExecutionRunEntity;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.security.TestSecurity;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Pins platform-aggregator.enabled instead of relying on its default, which gets flipped locally for manual testing.
@QuarkusTest
@TestProfile(FederatedTrainingPlatformCoordinatorDisabledTest.PlatformAggregatorDisabledProfile.class)
class FederatedTrainingPlatformCoordinatorDisabledTest extends FederatedTrainingPlatformCoordinatorTestBase {

    @Test
    @TestSecurity(user = "test", roles = "admin")
    void createRefusesThePlatformAsCoordinator() {
        long projectId = createProjectWithSingleNodeWorkflowTransactional();

        postTraining(projectId, create(true)).statusCode(405);
    }

    // The clinics accepted knowing the platform aggregates; a clinic must not take over once the platform cannot.
    @Test
    @TestSecurity(user = "test", roles = "admin")
    void startFailsWhenThePlatformCanNoLongerCoordinate() {
        long projectId = createProjectWithSingleNodeWorkflowTransactional();
        long runId = createTraining(projectId, false);
        // Set after creation, as if the deployment disabled the platform aggregator meanwhile.
        updateExperimentTransactional(runId, experiment -> experiment.setPlatformIsCoordinator(true));
        acceptByClinics(runId);

        startTraining(projectId, runId).statusCode(405);

        ExecutionRunEntity afterFailedStart = loadRunTransactional(runId);
        assertEquals(ProjectStatus.READY, afterFailedStart.getExperiment().getStatus(),
                "a failed start leaves the training READY, so it can be retried");
        assertTrue(afterFailedStart.getParticipants().stream().noneMatch(ExecutionParticipantEntity::isCoordinator));
    }

    public static class PlatformAggregatorDisabledProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("flnet.federated-learning.platform-aggregator.enabled", "false");
        }
    }
}
