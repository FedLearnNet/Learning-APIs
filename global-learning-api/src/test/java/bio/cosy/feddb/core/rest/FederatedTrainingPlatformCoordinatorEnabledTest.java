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

import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Pins platform-aggregator.enabled instead of relying on its default, which gets flipped locally for manual testing.
@QuarkusTest
@TestProfile(FederatedTrainingPlatformCoordinatorEnabledTest.PlatformAggregatorEnabledProfile.class)
class FederatedTrainingPlatformCoordinatorEnabledTest extends FederatedTrainingPlatformCoordinatorTestBase {

    @Test
    @TestSecurity(user = "test", roles = "admin")
    void startMarksNoClinicCoordinatorWhenThePlatformCoordinates() {
        long projectId = createProjectWithSingleNodeWorkflowTransactional();
        long runId = createTraining(projectId, true);
        acceptByClinics(runId);

        startTraining(projectId, runId)
                .statusCode(200)
                .body("experiment.status", is("RUNNING"))
                .body("experiment.platformIsCoordinator", is(true))
                .body("participants", hasSize(2))
                .body("participants.coordinator", everyItem(is(false)));

        ExecutionRunEntity started = loadRunTransactional(runId);
        assertEquals(ProjectStatus.RUNNING, started.getExperiment().getStatus());
        assertTrue(started.getParticipants().stream().noneMatch(ExecutionParticipantEntity::isCoordinator));
    }

    @Test
    @TestSecurity(user = "test", roles = "admin")
    void startMarksOneClinicCoordinatorWhenAClinicCoordinates() {
        long projectId = createProjectWithSingleNodeWorkflowTransactional();
        long runId = createTraining(projectId, false);
        acceptByClinics(runId);

        startTraining(projectId, runId).statusCode(200).body("experiment.platformIsCoordinator", is(false));

        assertEquals(1, loadRunTransactional(runId).getParticipants().stream().filter(ExecutionParticipantEntity::isCoordinator).count());
    }

    public static class PlatformAggregatorEnabledProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("flnet.federated-learning.platform-aggregator.enabled", "true");
        }
    }
}
