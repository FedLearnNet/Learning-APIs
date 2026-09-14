package rest;


import bio.cosy.feddb.local.api.learning.project.FederatedLearningProjectBO;
import bio.cosy.feddb.local.api.learning.project.run.FederatedLearningExperimentBroadcastBO;
import bio.cosy.feddb.local.api.learning.project.run.step.FederatedLearningExperimentStepAO;
import bio.cosy.feddb.local.api.learning.project.run.step.FederatedLearningExperimentStepBO;
import bio.cosy.feddb.local.services.KeycloakService;
import bio.cosy.feddb.local.services.orch.WorkflowOrchestratorBO;
import io.quarkus.logging.Log;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import rest.resource.LocalRelayService;

import static org.mockito.Mockito.when;

@QuarkusTest
@Transactional
public class LearningServiceE2ETest {

    @Inject
    WorkflowOrchestratorBO workflowOrchestratorBO;

    @InjectMock
    KeycloakService keycloakService;

    @Inject
    FederatedLearningExperimentStepBO federatedLearningExperimentStepBO;

    @Inject
    FederatedLearningExperimentBroadcastBO federatedLearningExperimentBroadcastBO;

    @Inject
    FederatedLearningProjectBO federatedLearningProjectBO;

    @Inject
    FederatedLearningExperimentStepAO federatedLearningExperimentStepAO;

    @Inject
    @RestClient
    LocalRelayService localRelayService;

    @BeforeEach
    void setup() {
        when(keycloakService.getServiceAccountToken()).thenReturn("");
    }

    @AfterEach
    void tearDown() {
        Log.info("Cleaning up after test...");
        workflowOrchestratorBO.cleanAll();
    }
/*
    @Test
    @TestTransaction
    void testHandleStartLearningRequestProjectNotFound() {
        // GIVEN
        long requestId = 42L;
        StartLearningClientRequestDTO request = new StartLearningClientRequestDTO(requestId, 0L, "");
        Optional<LearningClientSyncResponseDTO> result = federatedLearningExperimentStepBO
                .handleStartLearningRequest(request);

        assertNotEquals(Optional.empty(), result);
        assertEquals(FedDBClientResponseType.ERROR, result.get().getType());
    }

    @Test
    @TestTransaction
    void testHandleStartLearningRequestIncorrectStep() {
        // GIVEN
        long requestId = 1L;
        StartLearningClientRequestDTO request = new StartLearningClientRequestDTO(requestId, 5L, "");
        Optional<LearningClientSyncResponseDTO> result = federatedLearningExperimentStepBO
                .handleStartLearningRequest(request);

        assertNotEquals(Optional.empty(), result);
        assertEquals(FedDBClientResponseType.ERROR, result.get().getType());
    }

    @Test
    @TestTransaction
    void testHandleStartLearningRequestAppStarts() {
        // GIVEN
        long requestId = 1L;
        checkStartup(requestId, 0L);
    }

    @Test
    @TestTransaction
    void testHandleStartLearningRequestAppConnects() {
        // GIVEN
        long requestId = 1L;
        FederatedLearningExperimentStepEntity step = checkStartup(requestId, 0L);

        // Now, wait until the asynchronous update in the DB has been applied.
        await().atMost(10, TimeUnit.SECONDS)
                .until(() -> stepHasStatus(step.getId(), ProjectStatus.READY));
    }

    @Test
    @TestTransaction
    void testHandleStartLearningRequestAppFinished() {
        // GIVEN
        long requestId = 1L;
        FederatedLearningExperimentStepEntity step = checkStartup(requestId, 0L);

        // Now, wait until the asynchronous update in the DB has been applied.
        await().atMost(10, TimeUnit.SECONDS)
                .until(() -> stepHasStatus(step.getId(), ProjectStatus.READY));

        federatedLearningExperimentBroadcastBO.startStep(step.getId());
        //TODO ERROR FOR KNOW CUZ OF 401 Client Error: Unauthorized error
        //await().atMost(20, TimeUnit.SECONDS)
         //       .until(() -> stepHasStatus(step.getId(), ProjectStatus.ERROR));
    }


    @Test
    @TestTransaction
    void testHandleStartLearningRequestAppConnectsOldFc() {
        // GIVEN
        long requestId = 2L;
        FederatedLearningExperimentStepEntity step = checkStartup(requestId, 0L);
        // Now, wait until the asynchronous update in the DB has been applied.
        await().atMost(10, TimeUnit.SECONDS)
                .until(() -> stepHasStatus(step.getId(), ProjectStatus.READY));

        String channelId = step.getExperiment().getProject().getRequest().getChannelId();
        assertNotNull(channelId);

        //Start learning
        LearningClientSyncRequestDTO startLearning = LearningClientSyncRequestDTO.createRunRequest(0, requestId);
        Optional<LearningClientSyncResponseDTO> responseData = federatedLearningExperimentStepBO.handleNextStep(startLearning);
        responseData.ifPresent(response -> assertEquals(FedDBClientResponseType.LEARNING_SYNC, response.getType()));

        await().atMost(20, TimeUnit.SECONDS)
                .until(() -> stepHasStatus(step.getId(), ProjectStatus.RUNNING));

        // Now, wait until the asynchronous update in the DB has been applied.
        // This is a workaround to not mock global learning service, as it is not needed for this test.
        try (Response response = localRelayService.setup(channelId)) {
            assertEquals(200, response.getStatus());
        } catch (Exception e) {
            fail("Failed to setup relay service: " + e.getMessage());
        }
        await().atMost(20, TimeUnit.SECONDS)
                .until(() -> stepHasStatus(step.getId(), ProjectStatus.RUNNING));
            Log.info("Waited for app to start");

        await().atMost(200, TimeUnit.SECONDS)
                .until(() -> stepHasStatus(step.getId(), ProjectStatus.FINISHED));

    }


    @Test
    @TestTransaction
    void testHandleStartLearningRequestAppConnectsOldFcFailed() {
        // GIVEN
        long requestId = 6L;
        FederatedLearningExperimentStepEntity step = checkStartup(requestId, 0L);
        // Now, wait until the asynchronous update in the DB has been applied.
        await().atMost(10, TimeUnit.SECONDS)
                .until(() -> stepHasStatus(step.getId(), ProjectStatus.READY));

        String channelId = step.getExperiment().getProject().getRequest().getChannelId();
        assertNotNull(channelId);

        //Start learning
        LearningClientSyncRequestDTO startLearning = LearningClientSyncRequestDTO.createRunRequest(0, requestId);
        Optional<LearningClientSyncResponseDTO> responseData = federatedLearningExperimentStepBO.handleNextStep(startLearning);
        responseData.ifPresent(response -> assertEquals(FedDBClientResponseType.LEARNING_SYNC, response.getType()));

        await().atMost(20, TimeUnit.SECONDS)
                .until(() -> stepHasStatus(step.getId(), ProjectStatus.RUNNING));

        // Now, wait until the asynchronous update in the DB has been applied.
        // This is a workaround to not mock global learning service, as it is not needed for this test.
        try (Response response = localRelayService.setup(channelId)) {
            assertEquals(200, response.getStatus());
        } catch (Exception e) {
            fail("Failed to setup relay service: " + e.getMessage());
        }
        await().atMost(20, TimeUnit.SECONDS)
                .until(() -> stepHasStatus(step.getId(), ProjectStatus.RUNNING));
        Log.info("Waited for app to start");

        await().atMost(200, TimeUnit.SECONDS)
                .until(() -> stepHasStatus(step.getId(), ProjectStatus.ERROR));

    }

    //@Test TODO LATER
    void testFCWorkflow() {
        // GIVEN
        long requestId = 3L;
        FederatedLearningExperimentStepEntity step = checkStartup(requestId, 0L);

        // Now, wait until the asynchronous update in the DB has been applied.
        await().atMost(10, TimeUnit.SECONDS)
                .until(() -> stepHasStatus(step.getId(), ProjectStatus.READY));

        String channelId = step.getExperiment().getProject().getRequest().getChannelId();
        assertNotNull(channelId);

        //Start learning
        LearningClientSyncRequestDTO startLearning = LearningClientSyncRequestDTO.createRunRequest(0, requestId);
        Optional<LearningClientSyncResponseDTO> responseData = federatedLearningExperimentStepBO.handleNextStep(startLearning);
        responseData.ifPresent(response -> assertEquals(FedDBClientResponseType.LEARNING_SYNC, response.getType()));

        await().atMost(20, TimeUnit.SECONDS)
                .until(() -> stepHasStatus(step.getId(), ProjectStatus.RUNNING));

        // Now, wait until the asynchronous update in the DB has been applied.
        // This is a workaround to not mock global learning service, as it is not needed for this test.
        try (Response response = localRelayService.setup(channelId)) {
            assertEquals(200, response.getStatus());
        } catch (Exception e) {
            fail("Failed to setup relay service: " + e.getMessage());
        }
        await().atMost(20, TimeUnit.SECONDS)
                .until(() -> stepHasStatus(step.getId(), ProjectStatus.RUNNING));
        Log.info("Waited for app to start");

        await().atMost(20, TimeUnit.SECONDS)
                .until(() -> stepHasStatus(step.getId(), ProjectStatus.FINISHED));
    }

    @TestTransaction
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public FederatedLearningExperimentStepEntity checkStartup(long requestId, long step) {


        Optional<ProjectDetailDTO> projectOptional = federatedLearningProjectBO.findByRequestIdOptionalTransactional(requestId);
        assertNotEquals(Optional.empty(), projectOptional);

        Optional<FederatedLearningExperimentStepEntity> stepEntityOptional = federatedLearningExperimentStepAO.findByProjectIdAndStep(projectOptional.get().getId(), step);
        assertNotEquals(Optional.empty(), stepEntityOptional);

        FederatedLearningExperimentStepEntity stepEntity = stepEntityOptional.get();

        StartLearningClientRequestDTO request = new StartLearningClientRequestDTO(requestId, step, "");
        Optional<LearningClientSyncResponseDTO> result = federatedLearningExperimentStepBO
                .handleStartLearningRequest(request);

        Log.info("Waiting for app to start...");

        assertNotEquals(Optional.empty(), result);

        assertEquals(FedDBClientResponseType.LEARNING_SYNC, result.get().getType());

        return stepEntity;
    }

    @TestTransaction
    @Transactional
    public boolean stepHasStatus(Long stepId, ProjectStatus status) {
        return federatedLearningExperimentStepBO.getById(stepId).getStepStatus() == status;
    }
    */
}
