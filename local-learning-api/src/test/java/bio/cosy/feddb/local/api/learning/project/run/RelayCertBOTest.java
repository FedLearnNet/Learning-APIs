package bio.cosy.feddb.local.api.learning.project.run;

import bio.cosy.feddb.core.api.run.RunStatusTypes;
import bio.cosy.feddb.core.api.socket.FederatedLearningRelayInfoDTO;
import bio.cosy.feddb.core.api.socket.RelayCertSignRequestDTO;
import bio.cosy.feddb.core.api.socket.RelayCertSignResponseDTO;
import bio.cosy.feddb.core.services.controller.ControllerStartRelayingRequestDTO;
import bio.cosy.feddb.local.api.eam.WebsocketSender;
import bio.cosy.feddb.local.api.learning.project.run.step.FederatedLearningExperimentStepAO;
import bio.cosy.feddb.local.api.learning.project.run.step.FederatedLearningExperimentStepBO;
import bio.cosy.feddb.local.api.learning.project.run.step.FederatedLearningExperimentStepDTO;
import bio.cosy.feddb.local.api.learning.project.run.step.FederatedLearningExperimentStepEntity;
import bio.cosy.feddb.local.api.learning.project.run.step.RelayCertRequest;
import bio.cosy.feddb.local.config.FLNetClientConfig;
import bio.cosy.feddb.local.services.controller.LocalControllerLearningService;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RelayCertBOTest {

    private static final long EXPERIMENT_ID = 3L;
    private static final long STEP_ID = 19L;
    private static final String EXPERIMENT = "experiment-1";
    private static final String CLINIC = "clinic-a";
    private static final String NODE = "node-1";
    private static final String CLIENT = "0a0b0c0d0e0f0a0b";
    private static final String CHANNEL = "channel-1";
    private static final String CSR = "-----BEGIN CERTIFICATE REQUEST-----";

    private FederatedLearningExperimentStepEntity step;

    private RelayCertBO relayCertBO() {
        RelayCertBO bo = new RelayCertBO();
        bo.stepBO = mock(FederatedLearningExperimentStepBO.class);
        bo.stepAO = mock(FederatedLearningExperimentStepAO.class);
        bo.experimentAO = mock(FederatedLearningExperimentAO.class);
        bo.websocketSender = mock(WebsocketSender.class);
        bo.controllerService = mock(LocalControllerLearningService.class);
        bo.config = mock(FLNetClientConfig.class, RETURNS_DEEP_STUBS);
        when(bo.config.relay().certSign().timeout()).thenReturn(Duration.ofMinutes(5));
        when(bo.config.relay().certSign().maxAttempts()).thenReturn(3);

        FederatedLearningExperimentEntity experiment = new FederatedLearningExperimentEntity();
        experiment.setId(EXPERIMENT_ID);
        experiment.setUniqueRandomClinicId(CLINIC);
        when(bo.experimentAO.getByGlobalRequestId(EXPERIMENT)).thenReturn(Optional.of(experiment));

        FederatedLearningRelayInfoDTO relay = new FederatedLearningRelayInfoDTO();
        relay.setId(CLIENT);
        relay.setChannel(CHANNEL);
        step = new FederatedLearningExperimentStepEntity();
        step.setId(STEP_ID);
        step.setStepStatus(RunStatusTypes.RUNNING);
        step.setRelayInfo(relay);
        step.setRelayCsr(CSR);
        when(bo.stepAO.findByWorkflowNodeId(EXPERIMENT_ID, NODE)).thenReturn(Optional.of(step));
        when(bo.stepAO.claimRelayCertSignedTransactional(STEP_ID)).thenReturn(true);

        FederatedLearningExperimentStepDTO stepDTO = new FederatedLearningExperimentStepDTO();
        stepDTO.setId(STEP_ID);
        stepDTO.setStepStatus(RunStatusTypes.RUNNING);
        when(bo.stepBO.getById(STEP_ID)).thenReturn(stepDTO);

        when(bo.controllerService.startRelaying(any())).thenReturn(Response.ok().build());
        return bo;
    }

    private RelayCertSignResponseDTO signed() {
        return RelayCertSignResponseDTO.createResponse(new RelayCertSignRequestDTO(EXPERIMENT, CLINIC, NODE, CLIENT, CSR), "signed-cert");
    }

    private RelayCertRequest pending(int attempts, Date requestedAt) {
        return new RelayCertRequest(STEP_ID, EXPERIMENT, CLINIC, NODE, CLIENT, CSR, attempts, requestedAt.toInstant());
    }

    private static Date minutesAgo(Date now, int minutes) {
        return new Date(now.getTime() - Duration.ofMinutes(minutes).toMillis());
    }

    private static void verifyStepFailed(RelayCertBO bo) {
        ArgumentCaptor<FederatedLearningExperimentStepDTO> failed = ArgumentCaptor.forClass(FederatedLearningExperimentStepDTO.class);
        verify(bo.stepBO).updateStatus(failed.capture());
        assertEquals(RunStatusTypes.ERROR, failed.getValue().getStepStatus());
        assertTrue(failed.getValue().getLastError() != null && !failed.getValue().getLastError().isBlank());
    }

    @Test
    void requestCertificateStoresCsrAndSendsItToGlobal() {
        RelayCertBO bo = relayCertBO();
        when(bo.stepBO.startRelayCertRequest(STEP_ID, CSR)).thenReturn(pending(1, new Date()));

        bo.requestCertificate(STEP_ID, CSR);

        verify(bo.websocketSender).sendRelayCertRequest(new RelayCertSignRequestDTO(EXPERIMENT, CLINIC, NODE, CLIENT, CSR));
    }

    @Test
    void signedCertificateIsPassedToTheController() {
        RelayCertBO bo = relayCertBO();

        bo.handleSigned(signed());

        // the app key of the controller run is the relay client id
        verify(bo.controllerService).startRelaying(new ControllerStartRelayingRequestDTO(CHANNEL, CLIENT, "signed-cert"));
        verify(bo.stepAO).claimRelayCertSignedTransactional(STEP_ID);
        verify(bo.stepBO, never()).updateStatus(any(FederatedLearningExperimentStepDTO.class));
    }

    @Test
    void secondAnswerIsIgnored() {
        RelayCertBO bo = relayCertBO();
        // e.g. the answers to the first and to a retried request both arrive
        when(bo.stepAO.claimRelayCertSignedTransactional(STEP_ID)).thenReturn(false);

        bo.handleSigned(signed());

        verify(bo.controllerService, never()).startRelaying(any());
        verify(bo.stepBO, never()).updateStatus(any(FederatedLearningExperimentStepDTO.class));
    }

    @Test
    void certificateForAnotherClinicNodeOrClientIsIgnored() {
        RelayCertBO bo = relayCertBO();

        RelayCertSignResponseDTO otherClinic = signed();
        otherClinic.setUniqueRandomClinicId("clinic-b");
        bo.handleSigned(otherClinic);

        RelayCertSignResponseDTO otherExperiment = signed();
        otherExperiment.setGlobalUniqueExperimentId("experiment-unknown");
        when(bo.experimentAO.getByGlobalRequestId("experiment-unknown")).thenReturn(Optional.empty());
        bo.handleSigned(otherExperiment);

        RelayCertSignResponseDTO otherNode = signed();
        otherNode.setCurrentNodeId("node-2");
        when(bo.stepAO.findByWorkflowNodeId(EXPERIMENT_ID, "node-2")).thenReturn(Optional.empty());
        bo.handleSigned(otherNode);

        RelayCertSignResponseDTO otherClient = signed();
        otherClient.setClientId("ffffffffffffffff");
        bo.handleSigned(otherClient);

        verify(bo.stepAO, never()).claimRelayCertSignedTransactional(anyLong());
        verify(bo.controllerService, never()).startRelaying(any());
        verify(bo.stepBO, never()).updateStatus(any(FederatedLearningExperimentStepDTO.class));
    }

    @Test
    void certificateForFinishedOrStoppedStepIsIgnored() {
        RelayCertBO bo = relayCertBO();

        step.setStepStatus(RunStatusTypes.STOPPED);
        bo.handleSigned(signed());

        step.setStepStatus(RunStatusTypes.RUNNING);
        step.setRelayStopped(true);
        bo.handleSigned(signed());

        verify(bo.controllerService, never()).startRelaying(any());
    }

    @Test
    void refusedSigningFailsTheStep() {
        RelayCertBO bo = relayCertBO();
        RelayCertSignResponseDTO refused = RelayCertSignResponseDTO.createErrorResponse(
                new RelayCertSignRequestDTO(EXPERIMENT, CLINIC, NODE, CLIENT, CSR), "refused");

        bo.handleSigned(refused);

        verify(bo.controllerService, never()).startRelaying(any());
        verifyStepFailed(bo);
    }

    @Test
    void controllerThatCannotConnectFailsTheStep() {
        RelayCertBO bo = relayCertBO();
        when(bo.controllerService.startRelaying(any())).thenReturn(Response.serverError().build());

        bo.handleSigned(signed());

        verifyStepFailed(bo);
    }

    @Test
    void controllerErrorThrownByTheRestClientFailsTheStep() {
        RelayCertBO bo = relayCertBO();
        when(bo.controllerService.startRelaying(any())).thenThrow(new WebApplicationException(400));

        bo.handleSigned(signed());

        verifyStepFailed(bo);
    }

    @Test
    void retryLeavesRecentRequestsAlone() {
        RelayCertBO bo = relayCertBO();
        Date now = new Date();
        when(bo.stepBO.findPendingRelayCerts()).thenReturn(List.of(pending(1, minutesAgo(now, 4))));

        bo.retryPending(now);

        verify(bo.websocketSender, never()).sendRelayCertRequest(any());
        verify(bo.stepAO, never()).retryRelayCertRequestTransactional(anyLong());
    }

    @Test
    void retrySendsTheSameCsrAgainAfterTheTimeout() {
        RelayCertBO bo = relayCertBO();
        Date now = new Date();
        when(bo.stepBO.findPendingRelayCerts()).thenReturn(List.of(pending(2, minutesAgo(now, 6))));

        bo.retryPending(now);

        verify(bo.stepAO).retryRelayCertRequestTransactional(STEP_ID);
        verify(bo.websocketSender).sendRelayCertRequest(new RelayCertSignRequestDTO(EXPERIMENT, CLINIC, NODE, CLIENT, CSR));
        verify(bo.stepBO, never()).updateStatus(any(FederatedLearningExperimentStepDTO.class));
    }

    @Test
    void retryFailsTheStepAfterTheLastAttemptTimedOut() {
        RelayCertBO bo = relayCertBO();
        Date now = new Date();
        when(bo.stepBO.findPendingRelayCerts()).thenReturn(List.of(pending(3, minutesAgo(now, 6))));

        bo.retryPending(now);

        verify(bo.websocketSender, never()).sendRelayCertRequest(any());
        // claimed, so a late certificate cannot connect the controller of the failed step anymore
        verify(bo.stepAO).claimRelayCertSignedTransactional(STEP_ID);
        verifyStepFailed(bo);
    }
}
