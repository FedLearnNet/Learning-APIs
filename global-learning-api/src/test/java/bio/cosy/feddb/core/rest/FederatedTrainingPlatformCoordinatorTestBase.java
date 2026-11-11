package bio.cosy.feddb.core.rest;

import bio.cosy.feddb.core.api.app.PublishStatus;
import bio.cosy.feddb.core.api.project.ProjectDTO;
import bio.cosy.feddb.core.rest.helper.RestAssuredConfigUtil;
import de.unihamburg.daibetes.api.app.version.FederatedAppVersionAO;
import de.unihamburg.daibetes.api.app.version.FederatedAppVersionEntity;
import de.unihamburg.daibetes.api.project.ProjectBO;
import de.unihamburg.daibetes.api.project.ProjectCreateDTO;
import de.unihamburg.daibetes.api.project.training.federated.CreateProjectFederatedExperimentDTO;
import de.unihamburg.daibetes.api.project.training.federated.FederatedTrainingBO;
import de.unihamburg.daibetes.api.workflow.WorkflowAO;
import de.unihamburg.daibetes.api.workflow.WorkflowEntity;
import de.unihamburg.daibetes.api.workflow.node.WorkflowNodeAO;
import de.unihamburg.daibetes.api.workflow.node.WorkflowNodeEntity;
import de.unihamburg.daibetes.execution.experiment.ExecutionExperimentEntity;
import de.unihamburg.daibetes.execution.run.ExecutionRunAO;
import de.unihamburg.daibetes.execution.run.ExecutionRunEntity;
import io.restassured.http.ContentType;
import io.restassured.response.ValidatableResponse;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeAll;

import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import static io.restassured.RestAssured.given;

/**
 * A training across clinics on a workflow of one node whose app does not learn across clinics: no relay run and no
 * aggregator container, so only who coordinates is decided.
 */
abstract class FederatedTrainingPlatformCoordinatorTestBase {

    @Inject
    ProjectBO projectBO;

    @Inject
    WorkflowAO workflowAO;

    @Inject
    WorkflowNodeAO workflowNodeAO;

    @Inject
    FederatedAppVersionAO federatedAppVersionAO;

    @Inject
    ExecutionRunAO runAO;

    @Inject
    FederatedTrainingBO trainingBO;

    @BeforeAll
    static void setupMapper() {
        RestAssuredConfigUtil.initMapper();
    }

    protected CreateProjectFederatedExperimentDTO create(boolean platformIsCoordinator) {
        CreateProjectFederatedExperimentDTO create = new CreateProjectFederatedExperimentDTO();
        create.setName("Federated " + UUID.randomUUID());
        create.setDescription("Platform coordinator regression test");
        create.setModelNeedToBePublic(false);
        create.setPlatformIsCoordinator(platformIsCoordinator);
        return create;
    }

    protected ValidatableResponse postTraining(long projectId, CreateProjectFederatedExperimentDTO create) {
        return given()
                .contentType(ContentType.JSON)
                .body(create)
                .when()
                .post("/project/{id}/experiment/federated", projectId)
                .then();
    }

    protected long createTraining(long projectId, boolean platformIsCoordinator) {
        Number runId = postTraining(projectId, create(platformIsCoordinator)).statusCode(201).extract().path("id");
        return runId.longValue();
    }

    protected ValidatableResponse startTraining(long projectId, long runId) {
        return given()
                .when()
                .put("/project/{id}/experiment/federated/{runId}/start", projectId, runId)
                .then();
    }

    // Each clinic accepts in a transaction of its own, like over the websocket.
    protected void acceptByClinics(long runId) {
        String globalUniqueId = loadRunTransactional(runId).getExperiment().getGlobalUniqueId();
        trainingBO.acceptTransactional(globalUniqueId, 12, "clinic-a", true);
        trainingBO.acceptTransactional(globalUniqueId, 9, "clinic-b", false);
    }

    // With the experiment and participants loaded, for checks after the transaction.
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    ExecutionRunEntity loadRunTransactional(long runId) {
        ExecutionRunEntity run = runAO.findById(runId);
        run.getExperiment().getStatus();
        run.getParticipants().size();
        return run;
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    void updateExperimentTransactional(long runId, Consumer<ExecutionExperimentEntity> update) {
        update.accept(runAO.findById(runId).getExperiment());
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    long createProjectWithSingleNodeWorkflowTransactional() {
        ProjectCreateDTO projectCreateDTO = new ProjectCreateDTO();
        projectCreateDTO.setName("Project " + UUID.randomUUID());
        projectCreateDTO.setDescription("Platform coordinator regression test");
        projectCreateDTO.setQueryId(1L);
        ProjectDTO project = projectBO.create(projectCreateDTO, "test");

        WorkflowEntity workflow = new WorkflowEntity();
        workflow.setKeycloakId("test");
        workflow.setName("Workflow " + UUID.randomUUID());
        workflow.setDescription("Single-node workflow");
        workflow.setPublishStatus(PublishStatus.PUBLISHED);
        workflowAO.persist(workflow);

        FederatedAppVersionEntity appVersion = federatedAppVersionAO.findById(1L);
        appVersion.getFederatedApp().setSupportsFederatedLearning(false);

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
