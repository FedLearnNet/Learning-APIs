package rest;

import bio.cosy.feddb.core.api.app.FederatedAppDetailDTO;
import bio.cosy.feddb.core.api.app.PublishStatus;
import bio.cosy.feddb.core.api.app.config.ToolConfigDataType;
import bio.cosy.feddb.core.api.app.config.ToolConfigsDTO;
import bio.cosy.feddb.core.api.app.config.ToolInputConfigDTO;
import bio.cosy.feddb.core.api.project.ProjectDetailDTO;
import bio.cosy.feddb.core.api.project.ProjectStatus;
import bio.cosy.feddb.core.api.run.RunStatusTypes;
import bio.cosy.feddb.core.api.socket.LearningQueryClientResponseDTO;
import bio.cosy.feddb.core.api.socket.ProjectFederatedExperimentForLocalDTO;
import bio.cosy.feddb.core.api.workflow.WorkflowDTO;
import bio.cosy.feddb.core.api.workflow.connection.WorkflowConnectionDTO;
import bio.cosy.feddb.core.api.workflow.connection.WorkflowConnectionStatics;
import bio.cosy.feddb.core.api.workflow.node.WorkflowNodeDetailDTO;
import bio.cosy.feddb.local.api.cohort.patient.export.PatientDataExportBO;
import bio.cosy.feddb.local.api.cohort.patient.traceability.learning.PatientLearningAO;
import bio.cosy.feddb.local.api.cohort.patient.traceability.learning.PatientLearningDTO;
import bio.cosy.feddb.local.api.cohort.patient.traceability.learning.PatientLearningEntity;
import bio.cosy.feddb.local.api.eam.WebsocketSender;
import bio.cosy.feddb.local.api.learning.project.run.FederatedLearningExperimentAO;
import bio.cosy.feddb.local.api.learning.project.run.FederatedLearningExperimentBO;
import bio.cosy.feddb.local.api.learning.project.run.FederatedLearningExperimentEntity;
import bio.cosy.feddb.local.api.learning.project.run.data.FederatedLearningExperimentStepDataBO;
import bio.cosy.feddb.local.api.learning.project.run.data.StepResultContext;
import bio.cosy.feddb.local.api.learning.project.run.step.FederatedLearningExperimentStepBO;
import bio.cosy.feddb.local.api.learning.project.run.step.FederatedLearningExperimentStepDTO;
import bio.cosy.feddb.local.api.learning.request.*;
import bio.cosy.feddb.local.api.query.LocalQueryDTO;
import bio.cosy.feddb.local.api.query.QueryBO;
import bio.cosy.feddb.local.api.query.QueryResultDTO;
import bio.cosy.feddb.local.api.query.QueryResultWrapperDTO;
import bio.cosy.feddb.local.services.GlobalAPIStoreService;
import bio.cosy.feddb.local.services.KeycloakService;
import bio.cosy.feddb.local.services.orch.WorkflowOrchestratorBO;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.InjectMock;
import io.quarkus.test.common.http.TestHTTPEndpoint;
import io.quarkus.test.junit.QuarkusMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static io.restassured.RestAssured.given;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@QuarkusTest
@TestHTTPEndpoint(FederatedLearningRequestService.class)
class FederatedLearningRequestE2ETest {

    private static final String SEEDED_QUERY_GLOBAL_ID = "1e427196-9827-43fd-ae22-fc0f511694a9";

    @Inject
    FederatedLearningRequestBO requestBO;

    @Inject
    FederatedLearningRequestAO requestAO;

    @Inject
    FederatedLearningExperimentBO experimentBO;

    @Inject
    FederatedLearningExperimentAO experimentAO;

    @Inject
    FederatedLearningExperimentStepBO stepBO;

    @Inject
    PatientLearningAO patientLearningAO;

    @InjectMock
    WebsocketSender websocketSender;

    @InjectMock
    WorkflowOrchestratorBO workflowOrchestratorBO;

    @InjectMock
    FederatedLearningExperimentStepDataBO stepDataBO;

    @InjectMock
    PatientDataExportBO exportBO;

    @InjectMock
    KeycloakService keycloakService;

    @InjectMock
    QueryBO queryBO;

    @BeforeEach
    void setup() {
        reset(websocketSender, workflowOrchestratorBO, stepDataBO, exportBO, keycloakService, queryBO);
        when(keycloakService.getServiceAccountToken()).thenReturn("service-account-token");
        when(queryBO.findByGlobalIdOptionalTransactional(SEEDED_QUERY_GLOBAL_ID))
                .thenReturn(Optional.of(createSeededQuery()));
        when(queryBO.runQuery(any(LocalQueryDTO.class), anyString()))
                .thenReturn(createSeededQueryResult());
        GlobalAPIStoreService globalApiMock = mock(GlobalAPIStoreService.class);
        when(globalApiMock.getAppByVersion(anyLong())).thenReturn(createMockedAppDetail());
        QuarkusMock.installMockForType(globalApiMock, GlobalAPIStoreService.class, RestClient.LITERAL);
    }

    @Test
    @TestSecurity(user = "admin", roles = "admin")
    void incomingGlobalLearningRequestCreatesPendingRequestWithPatientsAndProject() {
        String globalExperimentId = "incoming-" + UUID.randomUUID();

        Optional<LearningQueryClientResponseDTO> response = requestBO.handleLearningRequest(
                buildIncomingRequest(globalExperimentId, true)
        );

        assertTrue(response.isEmpty());

        FederatedLearningRequestEntity created = requestAO.findByGlobalFLExperimentUniqueId(globalExperimentId)
                .orElseThrow();
        assertEquals(FederatedLearningRequestStatus.PENDING, created.getStatus());
        assertNotNull(created.getProject());
        assertFalse(created.getPatients().isEmpty());

        given()
                .when()
                .get("/{id}", created.getId())
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("id", is(created.getId().intValue()))
                .body("globalFLExperimentUniqueId", is(globalExperimentId))
                .body("status", is("PENDING"));
    }

    @Test
    @TestSecurity(user = "admin", roles = "admin")
    void approvingPendingRequestCreatesExperimentAndNotifiesGlobalSide() {
        String globalExperimentId = "approve-" + UUID.randomUUID();
        requestBO.handleLearningRequest(buildIncomingRequest(globalExperimentId, false));

        FederatedLearningRequestEntity pending = requestAO.findByGlobalFLExperimentUniqueId(globalExperimentId)
                .orElseThrow();

        FederatedLearningRequestDTO update = new FederatedLearningRequestDTO();
        update.setStatus(FederatedLearningRequestStatus.APPROVED);
        update.setModelCanBePublic(false);

        given()
                .contentType(ContentType.JSON)
                .body(update)
                .when()
                .put("/{id}", pending.getId())
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("status", is("APPROVED"))
                .body("modelCanBePublic", is(false));

        FederatedLearningExperimentEntity experiment = experimentAO.getByGlobalRequestId(globalExperimentId)
                .orElseThrow();
        assertNotNull(experiment.getUniqueRandomClinicId());
        assertEquals(1, experiment.getSteps().size());

        verify(websocketSender).sendLearningClientResponse(
                anyInt(),
                eq(globalExperimentId),
                anyString(),
                any()
        );
        verify(websocketSender).sendCurrentLearnings(argThat(ids -> ids.contains(globalExperimentId)));
    }

    @Test
    @TestSecurity(user = "admin", roles = "admin")
    void approvingWithASelectionKeepsOnlyTheSelectedPatients() {
        String globalExperimentId = "trim-" + UUID.randomUUID();
        requestBO.handleLearningRequest(buildIncomingRequest(globalExperimentId, true));

        Long requestId = inTransaction(() -> requestAO
                .findByGlobalFLExperimentUniqueId(globalExperimentId)
                .orElseThrow()
                .getId());

        List<Long> linkIds = loadPatientLinkIds(requestId);
        assertTrue(linkIds.size() > 1, "the seed has to provide more than one patient to trim");

        Long keptId = linkIds.getFirst();
        PatientLearningDTO selected = new PatientLearningDTO();
        selected.setId(keptId);

        FederatedLearningRequestDTO update = new FederatedLearningRequestDTO();
        update.setStatus(FederatedLearningRequestStatus.APPROVED);
        update.setModelCanBePublic(true);
        update.setRequestPatients(List.of(selected));

        given()
                .contentType(ContentType.JSON)
                .body(update)
                .when()
                .put("/{id}", requestId)
                .then()
                .statusCode(200)
                .body("status", is("APPROVED"));

        assertEquals(List.of(keptId), loadPatientLinkIds(requestId),
                "everything the reviewer did not select has to be gone");
    }

    @Test
    @TestSecurity(user = "admin", roles = "admin")
    void rejectingRequestKeepsItAccessibleAfterItsPatientsAreRemoved() {
        String globalExperimentId = "reject-" + UUID.randomUUID();
        requestBO.handleLearningRequest(buildIncomingRequest(globalExperimentId, true));

        Long requestId = inTransaction(() -> requestAO
                .findByGlobalFLExperimentUniqueId(globalExperimentId)
                .orElseThrow()
                .getId());

        FederatedLearningRequestDTO update = new FederatedLearningRequestDTO();
        update.setStatus(FederatedLearningRequestStatus.REJECTED);

        given()
                .contentType(ContentType.JSON)
                .body(update)
                .when()
                .put("/{id}", requestId)
                .then()
                .statusCode(200)
                .body("status", is("REJECTED"));

        given()
                .when()
                .get("/{id}", requestId)
                .then()
                .statusCode(200)
                .body("requestPatients.size()", is(0));
    }

    private List<Long> loadPatientLinkIds(Long requestId) {
        return inTransaction(() -> patientLearningAO.getAllForRequest(requestId).stream()
                .map(PatientLearningEntity::getId)
                .sorted()
                .toList());
    }

    private static <T> T inTransaction(java.util.function.Supplier<T> work) {
        return QuarkusTransaction.requiringNew().call(work::get);
    }

    @Test
    @TestSecurity(user = "admin", roles = "admin")
    void localRunLifecycleStartsWorkflowAndMarksRequestCompletedOnFinalStep() throws Exception {
        String globalExperimentId = "run-" + UUID.randomUUID();
        requestBO.handleLearningRequest(buildIncomingRequest(globalExperimentId, true));

        FederatedLearningRequestEntity pending = requestAO.findByGlobalFLExperimentUniqueId(globalExperimentId)
                .orElseThrow();
        FederatedLearningRequestDTO approval = new FederatedLearningRequestDTO();
        approval.setStatus(FederatedLearningRequestStatus.APPROVED);
        approval.setModelCanBePublic(true);

        given()
                .contentType(ContentType.JSON)
                .body(approval)
                .when()
                .put("/{id}", pending.getId())
                .then()
                .statusCode(200)
                .body("status", is("APPROVED"));

        FederatedLearningExperimentEntity experiment = experimentAO.getByGlobalRequestId(globalExperimentId)
                .orElseThrow();

        Path inputFile = Files.createTempFile("federated-learning-input-", ".csv");
        Files.writeString(inputFile, "PATIENT_ID,feature_5001\n1,1\n");
        when(workflowOrchestratorBO.executeWorkflow(any(), anyString())).thenReturn("container-123");
        when(exportBO.exportDataForLearning(any(), anyLong(), anyLong())).thenReturn(inputFile);

        ProjectStatus startStatus = experimentBO.startLearning(experiment, true);
        assertEquals(ProjectStatus.INIT, startStatus);

        FederatedLearningExperimentEntity started = experimentAO.findById(experiment.getId());
        FederatedLearningExperimentStepDTO step = started.getSteps().stream()
                .findFirst()
                .map(entity -> stepBO.getById(experiment.getId(), entity.getId()))
                .orElseThrow();
        assertEquals(RunStatusTypes.PENDING, step.getStepStatus());
        verify(workflowOrchestratorBO).executeWorkflow(any(), anyString());
        verify(exportBO, atLeastOnce()).exportDataForLearning(any(), anyLong(), anyLong());
        long expectedNodeExecutionOrder = started.getSteps().stream().findFirst().orElseThrow()
                .getWorkflowNode().getExecutionOrder();
        verify(workflowOrchestratorBO).uploadFilesToVolume(
                eq(experiment.getId()),
                eq(expectedNodeExecutionOrder),
                eq(inputFile),
                eq("training_data.csv")
        );

        step.setStepStatus(RunStatusTypes.FINISHED);
        stepBO.updateStatus(step);

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            assertEquals(FederatedLearningRequestStatus.COMPLETED, loadRequestStatus(globalExperimentId));
            assertEquals(ProjectStatus.FINISHED, loadExperimentStatus(experiment.getId()));
        });

        verify(stepDataBO).saveResults(any(StepResultContext.class));
        verify(workflowOrchestratorBO).cleanup(experiment.getId());
        // The last step finishing must report the run itself as finished, not just the step
        verify(websocketSender).sendRunningUpdate(
                eq(started.getSteps().stream().findFirst().orElseThrow().getWorkflowNode().getNodeId()),
                eq(globalExperimentId),
                eq(RunStatusTypes.FINISHED),
                eq(ProjectStatus.FINISHED),
                eq(started.getUniqueRandomClinicId())
        );
    }

    private ProjectFederatedExperimentForLocalDTO buildIncomingRequest(String globalExperimentId,
                                                                       boolean modelNeedToBePublic) {
        ProjectDetailDTO projectVersion = new ProjectDetailDTO();
        projectVersion.setName("Incoming Project " + globalExperimentId);
        projectVersion.setDescription("Generated for package E2E");
        projectVersion.setGlobalUniqueQueryId(SEEDED_QUERY_GLOBAL_ID);

        ProjectFederatedExperimentForLocalDTO dto = new ProjectFederatedExperimentForLocalDTO();
        dto.setGlobalUniqueId(globalExperimentId);
        dto.setName("Incoming Request " + globalExperimentId);
        dto.setDescription("Mocked global side");
        dto.setModelNeedToBePublic(modelNeedToBePublic);
        dto.setChannelId("channel-" + globalExperimentId);
        dto.setProjectVersion(projectVersion);
        dto.setWorkflow(createIncomingWorkflow(globalExperimentId));
        dto.setKeycloakId("admin");
        dto.setRoles(Set.of("admin"));
        return dto;
    }

    private WorkflowDTO createIncomingWorkflow(String globalExperimentId) {
        WorkflowNodeDetailDTO node = new WorkflowNodeDetailDTO();
        node.setNodeId("node-" + globalExperimentId);
        node.setFederatedAppVersionId(1L);
        node.setExecutionOrder(0);
        node.setHyperParams(new LinkedHashMap<>());

        WorkflowDTO workflow = new WorkflowDTO();
        workflow.setName("Incoming Workflow " + globalExperimentId);
        workflow.setDescription("Single-node mocked workflow");
        workflow.setPublishStatus(PublishStatus.PUBLISHED);
        workflow.setNodes(List.of(node));
        workflow.setConnections(List.of(createInputConnection(node)));
        return workflow;
    }

    private WorkflowConnectionDTO createInputConnection(WorkflowNodeDetailDTO node) {
        WorkflowConnectionDTO connection = new WorkflowConnectionDTO();
        connection.setInputId("app_" + node.getNodeId() + "_input_training_data");
        connection.setOutputId(WorkflowConnectionStatics.getInputNodeVariableName(UUID.randomUUID().toString()));
        connection.setInputConnection(true);
        connection.setInputNodeId(node.getNodeId());
        connection.setInputFileName("training_data.csv");
        connection.setInputConfigName("training_data");
        return connection;
    }

    private FederatedAppDetailDTO createMockedAppDetail() {
        ToolInputConfigDTO input = new ToolInputConfigDTO();
        input.setVariableName("training_data");
        input.setType(ToolConfigDataType.CSV);
        input.setRequired(true);

        ToolConfigsDTO appConfig = new ToolConfigsDTO();
        appConfig.setInput(List.of(input));

        FederatedAppDetailDTO app = new FederatedAppDetailDTO();
        app.setAppConfig(appConfig);
        return app;
    }

    private LocalQueryDTO createSeededQuery() {
        LocalQueryDTO query = new LocalQueryDTO();
        query.setId(1L);
        query.setGlobalQueryId(SEEDED_QUERY_GLOBAL_ID);
        return query;
    }

    private QueryResultWrapperDTO createSeededQueryResult() {
        QueryResultWrapperDTO wrapper = new QueryResultWrapperDTO();
        wrapper.setQueryId(1L);
        wrapper.setResult(List.of(new QueryResultDTO(1L, 4L)));
        wrapper.setPatientMatchSql(
                "SELECT DISTINCT patient_id FROM patient_data WHERE patient_id IN (1, 2, 3, 4)");
        return wrapper;
    }

    private FederatedLearningRequestStatus loadRequestStatus(String globalExperimentId) {
        return QuarkusTransaction.requiringNew().call(() -> requestAO.findByGlobalFLExperimentUniqueId(globalExperimentId)
                .orElseThrow()
                .getStatus());
    }

    private ProjectStatus loadExperimentStatus(Long experimentId) {
        return QuarkusTransaction.requiringNew().call(() -> experimentAO.findById(experimentId).getExperimentStatus());
    }
}
