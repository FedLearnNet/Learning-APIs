package bio.cosy.feddb.core.rest;

import bio.cosy.feddb.core.api.app.PublishStatus;
import bio.cosy.feddb.core.api.project.ProjectDTO;
import bio.cosy.feddb.core.api.project.ProjectStatus;
import bio.cosy.feddb.core.rest.helper.RestAssuredConfigUtil;
import de.unihamburg.daibetes.api.app.version.FederatedAppVersionAO;
import de.unihamburg.daibetes.api.app.version.FederatedAppVersionEntity;
import de.unihamburg.daibetes.api.project.ProjectAO;
import de.unihamburg.daibetes.api.project.ProjectBO;
import de.unihamburg.daibetes.api.project.ProjectCreateDTO;
import de.unihamburg.daibetes.api.project.ProjectEntity;
import de.unihamburg.daibetes.api.project.experiment.federated.CreateProjectFederatedExperimentDTO;
import de.unihamburg.daibetes.api.project.experiment.federated.ProjectFederatedExperimentAO;
import de.unihamburg.daibetes.api.project.experiment.federated.ProjectFederatedExperimentBO;
import de.unihamburg.daibetes.api.project.experiment.federated.ProjectFederatedExperimentEntity;
import de.unihamburg.daibetes.api.workflow.WorkflowAO;
import de.unihamburg.daibetes.api.workflow.WorkflowEntity;
import de.unihamburg.daibetes.api.workflow.node.WorkflowNodeAO;
import de.unihamburg.daibetes.api.workflow.node.WorkflowNodeEntity;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

// Pins flnet.federated-learning.platform-aggregator.enabled=true explicitly rather than relying
// on FLNetConfig's literal @WithDefault value, since that default gets flipped locally for manual
// testing of the enabled path.
//
// Uses a non-federated-learning workflow (supportsFederatedLearning=false) deliberately: relay
// setup and the actual aggregator container start (orch-api call) only happen for FL-enabled
// nodes (see ProjectFederatedExperimentStepBO.handleRelaySetup), so this test exercises the
// startLearning() coordinator-selection decision in isolation without needing a live orch-api.
@QuarkusTest
@TestProfile(ProjectFederatedExperimentPlatformCoordinatorEnabledTest.PlatformAggregatorEnabledProfile.class)
class ProjectFederatedExperimentPlatformCoordinatorEnabledTest {

    @Inject
    ProjectBO projectBO;

    @Inject
    ProjectAO projectAO;

    @Inject
    WorkflowAO workflowAO;

    @Inject
    WorkflowNodeAO workflowNodeAO;

    @Inject
    FederatedAppVersionAO federatedAppVersionAO;

    @Inject
    ProjectFederatedExperimentAO experimentAO;

    @Inject
    ProjectFederatedExperimentBO experimentBO;

    @BeforeAll
    static void setupMapper() {
        RestAssuredConfigUtil.initMapper();
    }

    @Test
    @TestSecurity(user = "test", roles = "admin")
    void startFederatedExperimentMarksNoClinicCoordinatorWhenPlatformAggregatorEnabled() {
        long projectId = createProjectWithSingleNodeWorkflow(false);
        setPlatformIsCoordinator(projectId, true);
        long experimentId = createExperiment(projectId);
        ProjectFederatedExperimentEntity created = loadExperiment(experimentId);

        acceptParticipant(created.getGlobalUniqueId(), 12, "clinic-a", true);
        acceptParticipant(created.getGlobalUniqueId(), 9, "clinic-b", false);

        startExperiment(projectId, experimentId);

        ProjectFederatedExperimentEntity started = loadExperiment(experimentId);
        assertEquals(ProjectStatus.RUNNING, started.getExperimentStatus());
        assertNull(started.getCoordinator(), "platform-aggregator support is enabled - "
                + "no clinic should be marked coordinator");
    }

    private void setPlatformIsCoordinator(long projectId, boolean platformIsCoordinator) {
        QuarkusTransaction.requiringNew().run(() -> {
            ProjectEntity project = projectAO.findById(projectId);
            project.setPlatformIsCoordinator(platformIsCoordinator);
            projectAO.persist(project);
        });
    }

    private long createExperiment(long projectId) {
        CreateProjectFederatedExperimentDTO dto = new CreateProjectFederatedExperimentDTO();
        dto.setName("Federated Platform Enabled " + UUID.randomUUID());
        dto.setDescription("Platform-coordinator-enabled regression test");
        dto.setModelNeedToBePublic(false);

        Number experimentId = given()
                .contentType(ContentType.JSON)
                .body(dto)
                .when()
                .post("/project/{id}/experiment/federated", projectId)
                .then()
                .statusCode(201)
                .extract()
                .path("id");
        return experimentId.longValue();
    }

    private void startExperiment(long projectId, long experimentId) {
        given()
                .when()
                .put("/project/{id}/experiment/federated/{eId}/start", projectId, experimentId)
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("experimentStatus", is("RUNNING"))
                .body("acceptanceClinicCount", is(2))
                .body("steps", hasSize(1));
    }

    private void acceptParticipant(String globalUniqueExperimentId, int count, String clinicId, boolean modelCanBePublic) {
        QuarkusTransaction.requiringNew()
                .run(() -> experimentBO.updateCount(globalUniqueExperimentId, count, clinicId, modelCanBePublic));
    }

    private ProjectFederatedExperimentEntity loadExperiment(long experimentId) {
        return QuarkusTransaction.requiringNew().call(() -> experimentAO.findById(experimentId));
    }

    private long createProjectWithSingleNodeWorkflow(boolean supportsFederatedLearning) {
        return QuarkusTransaction.requiringNew().call(() -> {
            ProjectCreateDTO projectCreateDTO = new ProjectCreateDTO();
            projectCreateDTO.setName("Project " + UUID.randomUUID());
            projectCreateDTO.setDescription("Platform-coordinator-enabled regression test");
            projectCreateDTO.setQueryId(1L);
            ProjectDTO project = projectBO.create(projectCreateDTO, "test");

            WorkflowEntity workflow = new WorkflowEntity();
            workflow.setKeycloakId("test");
            workflow.setName("Workflow " + UUID.randomUUID());
            workflow.setDescription("Single-node workflow");
            workflow.setPublishStatus(PublishStatus.PUBLISHED);
            workflowAO.persist(workflow);

            FederatedAppVersionEntity appVersion = federatedAppVersionAO.findById(1L);
            appVersion.getFederatedApp().setSupportsFederatedLearning(supportsFederatedLearning);

            WorkflowNodeEntity node = new WorkflowNodeEntity();
            node.setWorkflow(workflow);
            node.setFederatedAppVersion(appVersion);
            node.setNodeId("node-" + UUID.randomUUID());
            node.setExecutionOrder(0);
            node.setPosition("{\"x\":0,\"y\":0}");
            node.setHyperParams("{}");
            workflowNodeAO.persist(node);

            workflow.setNodes(Set.of(node));
            workflowAO.persist(workflow);
            projectBO.setWorkflow(project.getId(), "test", workflow);
            return project.getId();
        });
    }

    public static class PlatformAggregatorEnabledProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("flnet.federated-learning.platform-aggregator.enabled", "true");
        }
    }
}
