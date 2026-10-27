package bio.cosy.feddb.local.api.learning.project.run;

import bio.cosy.feddb.core.api.socket.RelayCertSignRequestDTO;
import bio.cosy.feddb.core.api.socket.RelayCertSignResponseDTO;
import bio.cosy.feddb.core.execution.run.dto.FederatedRunConfigDTO;
import bio.cosy.feddb.core.execution.run.types.RunStatusTypes;
import bio.cosy.feddb.core.services.controller.ControllerStartRelayingRequestDTO;
import bio.cosy.feddb.local.api.eam.WebsocketSender;
import bio.cosy.feddb.local.api.learning.project.FederatedLearningProjectAO;
import bio.cosy.feddb.local.api.learning.project.FederatedLearningProjectEntity;
import bio.cosy.feddb.local.api.learning.request.FederatedLearningRequestEntity;
import bio.cosy.feddb.local.api.workflow.node.WorkflowNodeEntity;
import bio.cosy.feddb.local.config.FLNetClientConfig;
import bio.cosy.feddb.local.execution.federation.ExecutionFederationAO;
import bio.cosy.feddb.local.execution.federation.ExecutionFederationEntity;
import bio.cosy.feddb.local.execution.run.ExecutionRunEntity;
import bio.cosy.feddb.local.execution.step.ExecutionRunStepAO;
import bio.cosy.feddb.local.execution.step.ExecutionRunStepEntity;
import bio.cosy.feddb.local.services.controller.LocalControllerLearningService;
import io.quarkus.hibernate.orm.panache.PanacheQuery;
import jakarta.persistence.LockModeType;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class RelayCertBOTest {

    private static final long RUN_ID = 3L;
    private static final long FEDERATION_ID = 19L;
    private static final String EXPERIMENT = "experiment-1";
    private static final String CLINIC = "clinic-a";
    private static final String NODE = "node-1";
    private static final String CLIENT = "0a0b0c0d0e0f0a0b";
    private static final String CHANNEL = "channel-1";
    private static final String CSR = "-----BEGIN CERTIFICATE REQUEST-----";

    private ExecutionRunStepEntity step;
    private ExecutionFederationEntity federation;

    @SuppressWarnings("unchecked")
    private RelayCertBO relayCertBO() {
        RelayCertBO bo = new RelayCertBO();
        bo.federationAO = mock(ExecutionFederationAO.class);
        bo.stepAO = mock(ExecutionRunStepAO.class);
        bo.projectAO = mock(FederatedLearningProjectAO.class);
        bo.federatedLearningRunBO = mock(FederatedLearningRunBO.class);
        bo.websocketSender = mock(WebsocketSender.class);
        bo.controllerService = mock(LocalControllerLearningService.class);
        bo.config = mock(FLNetClientConfig.class, RETURNS_DEEP_STUBS);
        when(bo.config.relay().certSign().timeout()).thenReturn(Duration.ofMinutes(5));
        when(bo.config.relay().certSign().maxAttempts()).thenReturn(3);

        ExecutionRunEntity run = new ExecutionRunEntity();
        run.setId(RUN_ID);
        run.setParticipantId(CLINIC);
        FederatedLearningRequestEntity request = new FederatedLearningRequestEntity();
        request.setGlobalFLExperimentUniqueId(EXPERIMENT);
        FederatedLearningProjectEntity project = new FederatedLearningProjectEntity();
        project.setRequest(request);
        project.setRun(run);
        when(bo.projectAO.findByRequestIdOptional(EXPERIMENT)).thenReturn(Optional.of(project));
        PanacheQuery<FederatedLearningProjectEntity> byRun = mock(PanacheQuery.class);
        when(byRun.firstResult()).thenReturn(project);
        when(bo.projectAO.find(eq("run.id"), eq(RUN_ID))).thenReturn(byRun);

        WorkflowNodeEntity node = new WorkflowNodeEntity();
        node.setNodeId(NODE);
        step = new ExecutionRunStepEntity();
        step.setId(7L);
        step.setRun(run);
        step.setWorkflowNode(node);
        step.setStatus(RunStatusTypes.RUNNING);
        FederatedRunConfigDTO config = new FederatedRunConfigDTO();
        config.setClientId(CLIENT);
        config.setChannel(CHANNEL);
        federation = new ExecutionFederationEntity();
        federation.setId(FEDERATION_ID);
        federation.setStep(step);
        federation.setConfig(config);
        federation.setRelayCsr(CSR);
        federation.setRelayCertSigned(false);
        federation.setRelayCertAttempts(1);
        step.setFederation(federation);
        when(bo.stepAO.findByNodeId(RUN_ID, NODE)).thenReturn(Optional.of(step));
        when(bo.federationAO.findByIdOptional(FEDERATION_ID, LockModeType.PESSIMISTIC_WRITE)).thenReturn(Optional.of(federation));

        when(bo.controllerService.startRelaying(any())).thenReturn(Response.ok().build());
        return bo;
    }

    private static RelayCertSignResponseDTO signed() {
        return RelayCertSignResponseDTO.createResponse(new RelayCertSignRequestDTO(EXPERIMENT, CLINIC, NODE, CLIENT, CSR), "signed-cert");
    }

    private void verifyStepFailed(RelayCertBO bo) {
        verify(bo.federatedLearningRunBO).fail(eq(step), anyString());
    }

    @Test
    void requestCertificateStoresCsrAndSendsItToGlobal() {
        RelayCertBO bo = relayCertBO();
        federation.setRelayCsr(null);
        federation.setRelayCertAttempts(null);

        bo.requestCertificate(federation, CSR);

        assertEquals(CSR, federation.getRelayCsr());
        assertEquals(1, federation.getRelayCertAttempts());
        verify(bo.websocketSender).sendRelayCertRequest(new RelayCertSignRequestDTO(EXPERIMENT, CLINIC, NODE, CLIENT, CSR));
    }

    @Test
    void signedCertificateIsPassedToTheController() {
        RelayCertBO bo = relayCertBO();

        bo.handleSignedTransactional(signed());

        // The app key of the controller run is the relay client id.
        verify(bo.controllerService).startRelaying(new ControllerStartRelayingRequestDTO(CHANNEL, CLIENT, "signed-cert"));
        assertTrue(federation.getRelayCertSigned());
        verify(bo.federatedLearningRunBO, never()).fail(any(), anyString());
    }

    @Test
    void secondAnswerIsIgnored() {
        RelayCertBO bo = relayCertBO();

        // E.g. the answers to the first and to a retried request both arrive.
        bo.handleSignedTransactional(signed());
        bo.handleSignedTransactional(signed());

        verify(bo.controllerService, times(1)).startRelaying(any());
    }

    @Test
    void certificateForAnotherClinicNodeOrClientIsIgnored() {
        RelayCertBO bo = relayCertBO();

        RelayCertSignResponseDTO otherClinic = signed();
        otherClinic.setUniqueRandomClinicId("clinic-b");
        bo.handleSignedTransactional(otherClinic);

        RelayCertSignResponseDTO otherExperiment = signed();
        otherExperiment.setGlobalUniqueExperimentId("experiment-unknown");
        when(bo.projectAO.findByRequestIdOptional("experiment-unknown")).thenReturn(Optional.empty());
        bo.handleSignedTransactional(otherExperiment);

        RelayCertSignResponseDTO otherNode = signed();
        otherNode.setCurrentNodeId("node-2");
        when(bo.stepAO.findByNodeId(RUN_ID, "node-2")).thenReturn(Optional.empty());
        bo.handleSignedTransactional(otherNode);

        RelayCertSignResponseDTO otherClient = signed();
        otherClient.setClientId("ffffffffffffffff");
        bo.handleSignedTransactional(otherClient);

        verify(bo.controllerService, never()).startRelaying(any());
        verify(bo.federatedLearningRunBO, never()).fail(any(), anyString());
        assertEquals(false, federation.getRelayCertSigned());
    }

    @Test
    void certificateForFinishedOrStoppedStepIsIgnored() {
        RelayCertBO bo = relayCertBO();

        step.setStatus(RunStatusTypes.STOPPED);
        bo.handleSignedTransactional(signed());

        step.setStatus(RunStatusTypes.RUNNING);
        federation.setRelayStopped(true);
        bo.handleSignedTransactional(signed());

        verify(bo.controllerService, never()).startRelaying(any());
    }

    @Test
    void refusedSigningFailsTheStep() {
        RelayCertBO bo = relayCertBO();
        RelayCertSignResponseDTO refused = RelayCertSignResponseDTO.createErrorResponse(
                new RelayCertSignRequestDTO(EXPERIMENT, CLINIC, NODE, CLIENT, CSR), "refused");

        bo.handleSignedTransactional(refused);

        verify(bo.controllerService, never()).startRelaying(any());
        verifyStepFailed(bo);
    }

    @Test
    void controllerThatCannotConnectFailsTheStep() {
        RelayCertBO bo = relayCertBO();
        when(bo.controllerService.startRelaying(any())).thenReturn(Response.serverError().build());

        bo.handleSignedTransactional(signed());

        verifyStepFailed(bo);
    }

    @Test
    void controllerErrorThrownByTheRestClientFailsTheStep() {
        RelayCertBO bo = relayCertBO();
        when(bo.controllerService.startRelaying(any())).thenThrow(new WebApplicationException(400));

        bo.handleSignedTransactional(signed());

        verifyStepFailed(bo);
    }

    @Test
    void retryLeavesRecentRequestsAlone() {
        RelayCertBO bo = relayCertBO();
        Instant now = Instant.now();
        federation.setRelayCertRequestedAt(now.minus(Duration.ofMinutes(4)));
        when(bo.federationAO.listPendingRelayCerts()).thenReturn(List.of(federation));

        bo.retryPending(now);

        verify(bo.websocketSender, never()).sendRelayCertRequest(any());
        assertEquals(1, federation.getRelayCertAttempts());
    }

    @Test
    void retrySendsTheSameCsrAgainAfterTheTimeout() {
        RelayCertBO bo = relayCertBO();
        Instant now = Instant.now();
        federation.setRelayCertAttempts(2);
        federation.setRelayCertRequestedAt(now.minus(Duration.ofMinutes(6)));
        when(bo.federationAO.listPendingRelayCerts()).thenReturn(List.of(federation));

        bo.retryPending(now);

        assertEquals(3, federation.getRelayCertAttempts());
        verify(bo.websocketSender).sendRelayCertRequest(new RelayCertSignRequestDTO(EXPERIMENT, CLINIC, NODE, CLIENT, CSR));
        verify(bo.federatedLearningRunBO, never()).fail(any(), anyString());
    }

    @Test
    void retryFailsTheStepAfterTheLastAttemptTimedOut() {
        RelayCertBO bo = relayCertBO();
        Instant now = Instant.now();
        federation.setRelayCertAttempts(3);
        federation.setRelayCertRequestedAt(now.minus(Duration.ofMinutes(6)));
        when(bo.federationAO.listPendingRelayCerts()).thenReturn(List.of(federation));

        bo.retryPending(now);

        verify(bo.websocketSender, never()).sendRelayCertRequest(any());
        // Claimed, so a late certificate cannot connect the controller of the failed step anymore.
        assertTrue(federation.getRelayCertSigned());
        verifyStepFailed(bo);
    }
}
