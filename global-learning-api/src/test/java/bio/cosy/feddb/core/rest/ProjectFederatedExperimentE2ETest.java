package bio.cosy.feddb.core.rest;

import bio.cosy.feddb.core.api.app.PublishStatus;
import bio.cosy.feddb.core.api.project.ProjectDTO;
import bio.cosy.feddb.core.api.project.ProjectStatus;
import bio.cosy.feddb.core.api.run.RunStatusTypes;
import bio.cosy.feddb.core.rest.helper.RestAssuredConfigUtil;
import de.unihamburg.daibetes.api.app.version.FederatedAppVersionAO;
import de.unihamburg.daibetes.api.app.version.FederatedAppVersionEntity;
import de.unihamburg.daibetes.api.project.ProjectBO;
import de.unihamburg.daibetes.api.project.ProjectCreateDTO;
import de.unihamburg.daibetes.api.project.experiment.federated.CreateProjectFederatedExperimentDTO;
import de.unihamburg.daibetes.api.project.experiment.federated.ProjectFederatedExperimentAO;
import de.unihamburg.daibetes.api.project.experiment.federated.ProjectFederatedExperimentBO;
import de.unihamburg.daibetes.api.project.experiment.federated.ProjectFederatedExperimentEntity;
import de.unihamburg.daibetes.api.project.experiment.federated.participants.ProjectFederatedExperimentParticipantAO;
import de.unihamburg.daibetes.api.project.experiment.federated.participants.ProjectFederatedExperimentParticipantEntity;
import de.unihamburg.daibetes.api.workflow.WorkflowAO;
import de.unihamburg.daibetes.api.workflow.WorkflowEntity;
import de.unihamburg.daibetes.api.workflow.node.WorkflowNodeAO;
import de.unihamburg.daibetes.api.workflow.node.WorkflowNodeEntity;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class ProjectFederatedExperimentE2ETest {

    @Inject
    ProjectBO projectBO;

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

    @Inject
    ProjectFederatedExperimentParticipantAO participantAO;

    @BeforeAll
    static void setupMapper() {
        RestAssuredConfigUtil.initMapper();
    }

    @Test
    @TestSecurity(user = "test", roles = "admin")
    void createFederatedExperimentBroadcastsLearningQueryWithWorkflowContext() {
        long projectId = createProjectWithSingleNodeWorkflow(false);

        CreateProjectFederatedExperimentDTO dto = new CreateProjectFederatedExperimentDTO();
        dto.setName("Federated Create " + UUID.randomUUID());
        dto.setDescription("Create flow");
        dto.setModelNeedToBePublic(true);

        Integer experimentId = given()
                .contentType(ContentType.JSON)
                .body(dto)
                .when()
                .post("/project/{id}/experiment/federated", projectId)
                .then()
                .statusCode(201)
                .contentType(ContentType.JSON)
                .body("projectId", is((int) projectId))
                .body("name", is(dto.getName()))
                .body("description", is(dto.getDescription()))
                .body("modelNeedToBePublic", is(true))
                .extract()
                .path("id");

        ProjectFederatedExperimentEntity created = experimentAO.findById(experimentId.longValue());
        assertNotNull(created);
        assertNotNull(created.getGlobalUniqueId());
        assertNotNull(created.getChannelId());
        assertEquals(ProjectStatus.INIT, created.getExperimentStatus());
        assertEquals(projectId, created.getProject().getId());
        assertNotNull(created.getWorkflow());
        assertEquals(1, created.getWorkflow().getNodes().size());
    }

    @Test
    @TestSecurity(user = "test", roles = "admin")
    void startFederatedExperimentCreatesStepsAndSelectsCoordinatorFromAcceptedParticipants() {
        long projectId = createProjectWithSingleNodeWorkflow(false);
        long experimentId = createExperiment(projectId, "Federated Start");
        ProjectFederatedExperimentEntity created = loadExperiment(experimentId);

        acceptParticipant(created.getGlobalUniqueId(), 12, "clinic-a", true);
        acceptParticipant(created.getGlobalUniqueId(), 9, "clinic-b", false);

        startExperiment(projectId, experimentId);

        ProjectFederatedExperimentEntity started = loadExperiment(experimentId);
        assertEquals(ProjectStatus.RUNNING, started.getExperimentStatus());
        assertNotNull(started.getCoordinator());
        assertEquals(1, loadStepCount(experimentId));
        assertFalse(Boolean.TRUE.equals(started.getModelCanBePublic()));

        List<ProjectFederatedExperimentParticipantEntity> participants = loadParticipants(experimentId);
        assertEquals(2, participants.size());
        assertTrue(participants.stream()
                .map(ProjectFederatedExperimentParticipantEntity::getUniqueRandomClinicId)
                .anyMatch(id -> id.equals(started.getCoordinator().getUniqueRandomClinicId())));
    }

    @Test
    @TestSecurity(user = "test", roles = "admin")
    void participantErrorStopsRunningExperimentAndBroadcastsStop() {
        long projectId = createProjectWithSingleNodeWorkflow(false);
        long experimentId = createExperiment(projectId, "Federated Error");
        ProjectFederatedExperimentEntity created = loadExperiment(experimentId);

        acceptParticipant(created.getGlobalUniqueId(), 8, "clinic-a", true);
        acceptParticipant(created.getGlobalUniqueId(), 7, "clinic-b", true);
        startExperiment(projectId, experimentId);

        ProjectFederatedExperimentParticipantEntity failingParticipant = loadParticipants(experimentId).getFirst();
        ProjectFederatedExperimentParticipantEntity refreshedParticipant = markParticipantAsFailed(failingParticipant.getId());

        notifyParticipantUpdate(refreshedParticipant);

        ProjectFederatedExperimentEntity failed = loadExperiment(experimentId);
        assertEquals(ProjectStatus.ERROR, failed.getExperimentStatus());
        assertTrue(loadParticipants(experimentId).stream()
                .allMatch(participant -> participant.getProjectStatus() == RunStatusTypes.STOPPED));
        assertTrue(loadParticipants(experimentId).stream()
                .allMatch(participant -> participant.getStepStatus() == ProjectStatus.ERROR));
    }

    @Test
    @TestSecurity(user = "test", roles = "admin")
    void finishedParticipantsFinishExperimentAndCurrentStepGlobally() {
        long projectId = createProjectWithSingleNodeWorkflow(false);
        long experimentId = createExperiment(projectId, "Federated Finish");
        ProjectFederatedExperimentEntity created = loadExperiment(experimentId);

        acceptParticipant(created.getGlobalUniqueId(), 8, "clinic-a", true);
        acceptParticipant(created.getGlobalUniqueId(), 7, "clinic-b", true);
        startExperiment(projectId, experimentId);

        List<ProjectFederatedExperimentParticipantEntity> participants = loadParticipants(experimentId);
        ProjectFederatedExperimentParticipantEntity firstParticipant = participants.get(0);
        ProjectFederatedExperimentParticipantEntity secondParticipant = participants.get(1);

        notifyParticipantUpdate(markParticipantStatus(firstParticipant.getId(), RunStatusTypes.RUNNING, null, ProjectStatus.READY));
        notifyParticipantUpdate(markParticipantStatus(secondParticipant.getId(), RunStatusTypes.RUNNING, null, ProjectStatus.READY));

        ProjectFederatedExperimentEntity runningExperiment = loadExperiment(experimentId);
        assertNotNull(runningExperiment.getCurrentWorkflowNode());
        assertEquals(RunStatusTypes.STARTED, runningExperiment.getCurrentWorkflowNode().getStepStatus());
        String currentNodeId = runningExperiment.getCurrentWorkflowNode().getWorkflowNode().getNodeId();

        notifyParticipantUpdate(markParticipantStatus(firstParticipant.getId(), RunStatusTypes.RUNNING, currentNodeId, ProjectStatus.FINISHED));
        notifyParticipantUpdate(markParticipantStatus(secondParticipant.getId(), RunStatusTypes.FINISHED, currentNodeId, ProjectStatus.FINISHED));

        ProjectFederatedExperimentEntity finishedExperiment = loadExperiment(experimentId);
        assertEquals(ProjectStatus.FINISHED, finishedExperiment.getExperimentStatus());
        assertEquals(RunStatusTypes.FINISHED, finishedExperiment.getCurrentWorkflowNode().getStepStatus());
        assertTrue(loadParticipants(experimentId).stream()
                .allMatch(participant -> participant.getProjectStatus() == RunStatusTypes.FINISHED));
        assertTrue(loadParticipants(experimentId).stream()
                .allMatch(participant -> participant.getStepStatus() == ProjectStatus.FINISHED));
    }

    @Test
    @TestSecurity(user = "test", roles = "admin")
    void sameClinicCanReportAdditionalPatientsWithoutDuplicatingParticipant() {
        long projectId = createProjectWithSingleNodeWorkflow(false);
        long experimentId = createExperiment(projectId, "Incremental Accept");
        ProjectFederatedExperimentEntity created = loadExperiment(experimentId);
        String clinicId = "clinic-incremental";

        acceptParticipant(created.getGlobalUniqueId(), 5, clinicId, true);
        acceptParticipant(created.getGlobalUniqueId(), 7, clinicId, false);

        ProjectFederatedExperimentEntity updated = loadExperiment(experimentId);
        assertEquals(12L, updated.getAcceptanceCount());
        assertEquals(1L, updated.getAcceptanceClinicCount());
        assertEquals(Boolean.FALSE, updated.getModelCanBePublic());
        assertEquals(1, loadParticipants(experimentId).size());
        assertEquals(ProjectStatus.READY, updated.getExperimentStatus());
    }

    private long createExperiment(long projectId, String namePrefix) {
        CreateProjectFederatedExperimentDTO dto = new CreateProjectFederatedExperimentDTO();
        dto.setName(namePrefix + " " + UUID.randomUUID());
        dto.setDescription("Federated experiment for package E2E");
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

    private void acceptParticipant(String globalUniqueExperimentId,
                                   int count,
                                   String clinicId,
                                   boolean modelCanBePublic) {
        QuarkusTransaction.requiringNew()
                .run(() -> experimentBO.updateCount(globalUniqueExperimentId, count, clinicId, modelCanBePublic));
    }

    private ProjectFederatedExperimentParticipantEntity markParticipantAsFailed(long participantId) {
        return QuarkusTransaction.requiringNew().call(() -> {
            participantAO.updateStatusTransactional(
                    participantId,
                    RunStatusTypes.ERROR,
                    "node-1",
                    ProjectStatus.ERROR
            );
            return participantAO.findById(participantId);
        });
    }

    private ProjectFederatedExperimentParticipantEntity markParticipantStatus(long participantId,
                                                                              RunStatusTypes projectStatus,
                                                                              String currentNodeId,
                                                                              ProjectStatus stepStatus) {
        return QuarkusTransaction.requiringNew().call(() -> {
            participantAO.updateStatusTransactional(
                    participantId,
                    projectStatus,
                    currentNodeId,
                    stepStatus
            );
            return participantAO.findById(participantId);
        });
    }

    private void notifyParticipantUpdate(ProjectFederatedExperimentParticipantEntity participant) {
        QuarkusTransaction.requiringNew().run(() -> experimentBO.updateStepAndNotify(participant));
    }

    private ProjectFederatedExperimentEntity loadExperiment(long experimentId) {
        return QuarkusTransaction.requiringNew().call(() -> experimentAO.findById(experimentId));
    }

    private List<ProjectFederatedExperimentParticipantEntity> loadParticipants(long experimentId) {
        return QuarkusTransaction.requiringNew().call(() -> participantAO.getAllByExperiment(experimentId));
    }

    private int loadStepCount(long experimentId) {
        return QuarkusTransaction.requiringNew().call(() -> experimentAO.findById(experimentId).getSteps().size());
    }

    private long createProjectWithSingleNodeWorkflow(boolean supportsFederatedLearning) {
        return QuarkusTransaction.requiringNew().call(() -> {
            ProjectCreateDTO projectCreateDTO = new ProjectCreateDTO();
            projectCreateDTO.setName("Project " + UUID.randomUUID());
            projectCreateDTO.setDescription("Federated package E2E");
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
}
