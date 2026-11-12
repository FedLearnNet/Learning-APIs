package bio.cosy.feddb.core.rest;

import bio.cosy.feddb.core.api.app.CommunicationEngine;
import bio.cosy.feddb.core.api.app.PublishStatus;
import bio.cosy.feddb.core.api.project.ProjectDTO;
import bio.cosy.feddb.core.rest.helper.RestAssuredConfigUtil;
import de.unihamburg.daibetes.api.app.version.FederatedAppVersionAO;
import de.unihamburg.daibetes.api.app.version.FederatedAppVersionEntity;
import de.unihamburg.daibetes.api.project.ProjectBO;
import de.unihamburg.daibetes.api.project.ProjectCreateDTO;
import de.unihamburg.daibetes.api.project.experiment.federated.CreateProjectFederatedExperimentDTO;
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

// Needs the platform-aggregator capability enabled so the happy path (Flower + platform as
// coordinator) isn't itself rejected by the unrelated capability check in
// ProjectBO.checkPlatformIsCoordinatorAllowed - see ProjectFederatedExperimentPlatformCoordinatorEnabledTest.
@QuarkusTest
@TestProfile(ProjectFederatedExperimentFlowerValidationTest.PlatformAggregatorEnabledProfile.class)
class ProjectFederatedExperimentFlowerValidationTest {

    @Inject
    ProjectBO projectBO;

    @Inject
    WorkflowAO workflowAO;

    @Inject
    WorkflowNodeAO workflowNodeAO;

    @Inject
    FederatedAppVersionAO federatedAppVersionAO;

    @BeforeAll
    static void setupMapper() {
        RestAssuredConfigUtil.initMapper();
    }

    @Test
    @TestSecurity(user = "test", roles = "admin")
    void createFederatedExperimentRejectsRandomClinicWhenWorkflowHasFlowerNode() {
        long projectId = createProjectWithSingleNodeWorkflow(CommunicationEngine.FLOWER);

        CreateProjectFederatedExperimentDTO dto = new CreateProjectFederatedExperimentDTO();
        dto.setName("Flower random-clinic rejection " + UUID.randomUUID());
        dto.setDescription("Should be rejected at creation time: Flower requires the platform as aggregator");
        dto.setPlatformIsCoordinator(false);

        given()
                .contentType(ContentType.JSON)
                .body(dto)
                .when()
                .post("/project/{id}/experiment/federated", projectId)
                .then()
                .statusCode(405);
    }

    @Test
    @TestSecurity(user = "test", roles = "admin")
    void createFederatedExperimentAllowsPlatformCoordinatorWhenWorkflowHasFlowerNode() {
        long projectId = createProjectWithSingleNodeWorkflow(CommunicationEngine.FLOWER);

        CreateProjectFederatedExperimentDTO dto = new CreateProjectFederatedExperimentDTO();
        dto.setName("Flower platform-coordinator " + UUID.randomUUID());
        dto.setDescription("Should be allowed: Flower workflow with the platform as aggregator");
        dto.setPlatformIsCoordinator(true);

        given()
                .contentType(ContentType.JSON)
                .body(dto)
                .when()
                .post("/project/{id}/experiment/federated", projectId)
                .then()
                .statusCode(201);
    }

    @Test
    @TestSecurity(user = "test", roles = "admin")
    void createFederatedExperimentAllowsRandomClinicWhenWorkflowHasNoFlowerNode() {
        long projectId = createProjectWithSingleNodeWorkflow(CommunicationEngine.FL_NET);

        CreateProjectFederatedExperimentDTO dto = new CreateProjectFederatedExperimentDTO();
        dto.setName("FL-Net random-clinic " + UUID.randomUUID());
        dto.setDescription("Should be allowed: no Flower tool in the workflow");
        dto.setPlatformIsCoordinator(false);

        given()
                .contentType(ContentType.JSON)
                .body(dto)
                .when()
                .post("/project/{id}/experiment/federated", projectId)
                .then()
                .statusCode(201);
    }

    private long createProjectWithSingleNodeWorkflow(CommunicationEngine communicationEngine) {
        return QuarkusTransaction.requiringNew().call(() -> {
            ProjectCreateDTO projectCreateDTO = new ProjectCreateDTO();
            projectCreateDTO.setName("Project " + UUID.randomUUID());
            projectCreateDTO.setDescription("Flower validation regression test");
            projectCreateDTO.setQueryId(1L);
            ProjectDTO project = projectBO.create(projectCreateDTO, "test");

            WorkflowEntity workflow = new WorkflowEntity();
            workflow.setKeycloakId("test");
            workflow.setName("Workflow " + UUID.randomUUID());
            workflow.setDescription("Single-node workflow");
            workflow.setPublishStatus(PublishStatus.PUBLISHED);
            workflowAO.persist(workflow);

            FederatedAppVersionEntity appVersion = federatedAppVersionAO.findById(1L);
            appVersion.getFederatedApp().setSupportsFederatedLearning(true);
            appVersion.getFederatedApp().setCommunicationEngine(communicationEngine);

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
