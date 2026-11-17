package bio.cosy.feddb.core.rest;

import bio.cosy.feddb.core.api.app.CommunicationEngine;
import bio.cosy.feddb.core.api.app.PublishStatus;
import bio.cosy.feddb.core.api.project.ProjectDTO;
import de.unihamburg.daibetes.api.app.version.FederatedAppVersionEntity;
import de.unihamburg.daibetes.api.project.ProjectCreateDTO;
import de.unihamburg.daibetes.api.project.training.federated.CreateProjectExperimentDTO;
import de.unihamburg.daibetes.api.workflow.WorkflowEntity;
import de.unihamburg.daibetes.api.workflow.node.WorkflowNodeEntity;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.security.TestSecurity;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

/**
 * Ticket #13's Task 2: a workflow containing a Flower-based tool requires the platform to coordinate - Flower's
 * aggregator (Tool/SuperLink bundle) needs its Fleet API port reachable by clinics' SuperNodes, which only the
 * platform is set up to expose.
 */
// Pins platform-aggregator.enabled explicitly: the "platform coordinates a Flower workflow" case needs it, and
// enabling it does not change the other two cases (checkPlatformIsCoordinatorAllowed only guards the true branch).
@QuarkusTest
@TestProfile(FederatedTrainingPlatformCoordinatorEnabledTest.PlatformAggregatorEnabledProfile.class)
class FederatedTrainingFlowerValidationTest extends FederatedTrainingPlatformCoordinatorTestBase {

    // federatedAppVersionAO.findById(1L) is a shared fixture reused by every test in this module (see
    // FederatedTrainingPlatformCoordinatorTestBase); tagging it as Flower here must not leak into later tests.
    @AfterEach
    void resetSharedAppVersionCommunicationEngine() {
        resetCommunicationEngineTransactional();
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    void resetCommunicationEngineTransactional() {
        federatedAppVersionAO.findById(1L).getFederatedApp().setCommunicationEngine(null);
    }

    @Test
    @TestSecurity(user = "test", roles = "admin")
    void rejectsRandomClinicCoordinationOfAFlowerWorkflow() {
        long projectId = createProjectWithFlowerWorkflowTransactional();

        postTraining(projectId, create(false)).statusCode(405);
    }

    @Test
    @TestSecurity(user = "test", roles = "admin")
    void allowsPlatformCoordinationOfAFlowerWorkflow() {
        long projectId = createProjectWithFlowerWorkflowTransactional();

        postTraining(projectId, create(true)).statusCode(201);
    }

    @Test
    @TestSecurity(user = "test", roles = "admin")
    void allowsRandomClinicCoordinationOfANonFlowerWorkflow() {
        long projectId = createProjectWithSingleNodeWorkflowTransactional();

        postTraining(projectId, create(false)).statusCode(201);
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    long createProjectWithFlowerWorkflowTransactional() {
        ProjectCreateDTO projectCreateDTO = new ProjectCreateDTO();
        projectCreateDTO.setName("Project " + UUID.randomUUID());
        projectCreateDTO.setDescription("Flower validation regression test");
        projectCreateDTO.setQueryId(1L);
        ProjectDTO project = projectBO.create(projectCreateDTO, "test");

        WorkflowEntity workflow = new WorkflowEntity();
        workflow.setKeycloakId("test");
        workflow.setName("Workflow " + UUID.randomUUID());
        workflow.setDescription("Single Flower-tagged node workflow");
        workflow.setPublishStatus(PublishStatus.PUBLISHED);
        workflowAO.persist(workflow);

        FederatedAppVersionEntity appVersion = federatedAppVersionAO.findById(1L);
        appVersion.getFederatedApp().setSupportsFederatedLearning(true);
        appVersion.getFederatedApp().setCommunicationEngine(CommunicationEngine.FLOWER);

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
    }
}
