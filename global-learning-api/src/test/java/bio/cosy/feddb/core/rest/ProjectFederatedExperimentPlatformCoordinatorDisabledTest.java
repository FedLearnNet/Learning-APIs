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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

// Pins flnet.federated-learning.platform-aggregator.enabled=false explicitly rather than relying
// on FLNetConfig's literal @WithDefault value, since that default gets flipped locally for manual
// testing of the enabled path (see ProjectFederatedExperimentPlatformCoordinatorEnabledTest).
@QuarkusTest
@TestProfile(ProjectFederatedExperimentPlatformCoordinatorDisabledTest.PlatformAggregatorDisabledProfile.class)
class ProjectFederatedExperimentPlatformCoordinatorDisabledTest {

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
    void startFederatedExperimentFailsWhenPlatformAggregatorDisabled() {
        // platformIsCoordinator=true was set while the capability was enabled elsewhere, then this
        // deployment disabled it again before the run starts (create()/update() validation would
        // otherwise block setting this combination directly). Participants already accepted this
        // run on the understanding the platform would aggregate - silently substituting a random
        // clinic instead would change that without their consent, so the start must fail instead.
        long projectId = createProjectWithSingleNodeWorkflow(false);
        setPlatformIsCoordinator(projectId, true);
        long experimentId = createExperiment(projectId);
        ProjectFederatedExperimentEntity created = loadExperiment(experimentId);

        acceptParticipant(created.getGlobalUniqueId(), 12, "clinic-a", true);
        acceptParticipant(created.getGlobalUniqueId(), 9, "clinic-b", false);

        given()
                .when()
                .put("/project/{id}/experiment/federated/{eId}/start", projectId, experimentId)
                .then()
                .statusCode(405);

        ProjectFederatedExperimentEntity afterFailedStart = loadExperiment(experimentId);
        assertEquals(ProjectStatus.READY, afterFailedStart.getExperimentStatus(), "a failed start must not move the "
                + "experiment out of READY, so the project owner can reconcile and retry");
        assertNull(afterFailedStart.getCoordinator());
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
        dto.setName("Federated Platform Disabled " + UUID.randomUUID());
        dto.setDescription("Platform-coordinator-disabled regression test");
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
            projectCreateDTO.setDescription("Platform-coordinator-disabled regression test");
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

    public static class PlatformAggregatorDisabledProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("flnet.federated-learning.platform-aggregator.enabled", "false");
        }
    }
}
