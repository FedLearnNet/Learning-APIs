package de.unihamburg.daibetes.api.project.training.federated;

import bio.cosy.feddb.core.api.socket.FederatedLearningRelayInfoDTO;
import bio.cosy.feddb.core.execution.event.ExecutionEventPublisher;
import bio.cosy.feddb.core.execution.run.dto.FederatedRunConfigDTO;
import bio.cosy.feddb.core.execution.run.dto.StartRunDTO;
import bio.cosy.feddb.core.execution.run.types.RunStatusTypes;
import bio.cosy.feddb.core.services.controller.ControllerStartLearningRequestDTO;
import bio.cosy.feddb.core.services.controller.ControllerStartLearningResponseDTO;
import bio.cosy.feddb.core.services.controller.ControllerStartRelayingRequestDTO;
import bio.cosy.feddb.core.services.controller.RelaySignCertRequestDTO;
import bio.cosy.feddb.core.services.controller.RelaySignCertResponseDTO;
import de.unihamburg.daibetes.execution.attempt.ExecutionAttemptAO;
import de.unihamburg.daibetes.execution.attempt.ExecutionAttemptEntity;
import de.unihamburg.daibetes.execution.step.ExecutionRunStepEntity;
import de.unihamburg.daibetes.services.GlobalRelayService;
import de.unihamburg.daibetes.services.LocalControllerLearningService;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class FederatedTrainingAggregatorBOTest {

    private static final Long ATTEMPT_ID = 42L;
    private static final String CHANNEL = "channel-1";
    private static final String CLIENT_ID = "coordinator-1";
    private static final String CSR = "-----BEGIN CERTIFICATE REQUEST-----";
    private static final String CERTIFICATE = "-----BEGIN CERTIFICATE-----";

    private FederatedTrainingAggregatorBO aggregatorBO() {
        FederatedTrainingAggregatorBO bo = new FederatedTrainingAggregatorBO();
        bo.attemptAO = mock(ExecutionAttemptAO.class);
        bo.events = mock(ExecutionEventPublisher.class);
        bo.controllerService = mock(LocalControllerLearningService.class);
        bo.globalRelayService = mock(GlobalRelayService.class);
        return bo;
    }

    private FederatedLearningRelayInfoDTO coordinatorSlot() {
        FederatedLearningRelayInfoDTO slot = new FederatedLearningRelayInfoDTO();
        slot.setId(CLIENT_ID);
        slot.setKey("client-key");
        slot.setChannel(CHANNEL);
        slot.setRelayKey("relay-key");
        slot.setCoordinator(true);
        slot.setCoordinatorId(CLIENT_ID);
        slot.setMaxNumClients(3);
        return slot;
    }

    private ExecutionAttemptEntity aggregator() {
        ExecutionAttemptEntity attempt = new ExecutionAttemptEntity();
        attempt.setId(ATTEMPT_ID);
        attempt.federationOrNew().setRelayConfig(coordinatorSlot().getRunConfig());
        return attempt;
    }

    private Response okStartLearningResponse(String csr) {
        Response response = statusResponse(Response.Status.OK.getStatusCode());
        ControllerStartLearningResponseDTO started = new ControllerStartLearningResponseDTO();
        started.setCsr(csr);
        when(response.readEntity(ControllerStartLearningResponseDTO.class)).thenReturn(started);
        return response;
    }

    private Response statusResponse(int status) {
        Response response = mock(Response.class);
        when(response.getStatus()).thenReturn(status);
        when(response.readEntity(String.class)).thenReturn("error body");
        return response;
    }

    @Test
    void addsAPendingAggregatorThatHoldsTheModelOfTheStep() {
        FederatedTrainingAggregatorBO bo = aggregatorBO();
        ExecutionRunStepEntity step = new ExecutionRunStepEntity();
        step.getAttempts().add(new ExecutionAttemptEntity());

        ExecutionAttemptEntity attempt = bo.addAttempt(step, coordinatorSlot(), new LinkedHashMap<>(Map.of("federated_rounds", 3)));

        assertEquals(RunStatusTypes.PENDING, attempt.getStatus());
        assertEquals(1, attempt.getTrialIndex());
        assertEquals(CLIENT_ID, attempt.getFederation().getRelayConfig().getClientId());
        assertSame(attempt, step.getSelectedAttempt());
        assertTrue(FederatedTrainingAggregatorBO.aggregatorOf(step).isPresent());
        verify(bo.attemptAO).persist(attempt);
        verify(bo.events).attemptLaunch(attempt);
    }

    @Test
    void clinicAttemptIsNoAggregator() {
        ExecutionAttemptEntity clinic = new ExecutionAttemptEntity();
        clinic.federationOrNew();

        assertFalse(FederatedTrainingAggregatorBO.isAggregator(clinic));
    }

    @Test
    void startupRunsTheAggregatorOnlyWithoutData() {
        StartRunDTO start = new StartRunDTO();
        start.setHyperParams(new LinkedHashMap<>(Map.of("federated_rounds", "5")));
        start.setInputFilePaths(new LinkedHashMap<>(Map.of("data", "data.csv")));

        aggregatorBO().customizeStartup(aggregator(), start);

        assertTrue(start.getStartAggregator());
        assertTrue(start.getAggregatorOnly());
        assertEquals(5, start.getTotalRounds());
        assertTrue(start.getInputFilePaths().isEmpty());
        assertEquals(CHANNEL, start.getConfig().getChannel());
        assertEquals(1, start.getParticipants().size());
        assertEquals("AGGREGATOR", start.getParticipants().getFirst().getRole());
        assertEquals(CLIENT_ID, start.getParticipants().getFirst().getParticipantId());
    }

    @Test
    void controllerJoinsTheRelayWithTheSignedCertificate() {
        FederatedTrainingAggregatorBO bo = aggregatorBO();
        Response startLearningResponse = okStartLearningResponse(CSR);
        Response startRelayingResponse = statusResponse(Response.Status.OK.getStatusCode());
        when(bo.controllerService.startLearning(any())).thenReturn(startLearningResponse);
        when(bo.globalRelayService.signCert(any())).thenReturn(new RelaySignCertResponseDTO(CERTIFICATE));
        when(bo.controllerService.startRelaying(any())).thenReturn(startRelayingResponse);

        bo.connectController(aggregator());

        FederatedRunConfigDTO relay = coordinatorSlot().getRunConfig();
        verify(bo.controllerService).startLearning(new ControllerStartLearningRequestDTO(relay, ATTEMPT_ID));
        verify(bo.globalRelayService).signCert(new RelaySignCertRequestDTO(CHANNEL, CLIENT_ID, CSR));
        verify(bo.controllerService).startRelaying(new ControllerStartRelayingRequestDTO(CHANNEL, CLIENT_ID, CERTIFICATE));
    }

    @Test
    void controllerRefusingTheRunFailsBeforeSigning() {
        FederatedTrainingAggregatorBO bo = aggregatorBO();
        Response startLearningResponse = statusResponse(Response.Status.BAD_REQUEST.getStatusCode());
        when(bo.controllerService.startLearning(any())).thenReturn(startLearningResponse);

        assertThrows(IllegalStateException.class, () -> bo.connectController(aggregator()));

        verifyNoInteractions(bo.globalRelayService);
    }

    @Test
    void blankCsrFailsBeforeSigning() {
        FederatedTrainingAggregatorBO bo = aggregatorBO();
        Response startLearningResponse = okStartLearningResponse(" ");
        when(bo.controllerService.startLearning(any())).thenReturn(startLearningResponse);

        assertThrows(IllegalStateException.class, () -> bo.connectController(aggregator()));

        verifyNoInteractions(bo.globalRelayService);
    }

    @Test
    void blankCertificateFailsBeforeRelaying() {
        FederatedTrainingAggregatorBO bo = aggregatorBO();
        Response startLearningResponse = okStartLearningResponse(CSR);
        when(bo.controllerService.startLearning(any())).thenReturn(startLearningResponse);
        when(bo.globalRelayService.signCert(any())).thenReturn(new RelaySignCertResponseDTO(" "));

        assertThrows(IllegalStateException.class, () -> bo.connectController(aggregator()));

        verify(bo.controllerService, never()).startRelaying(any());
    }

    @Test
    void controllerRefusingToRelayFails() {
        FederatedTrainingAggregatorBO bo = aggregatorBO();
        Response startLearningResponse = okStartLearningResponse(CSR);
        Response startRelayingResponse = statusResponse(Response.Status.FORBIDDEN.getStatusCode());
        when(bo.controllerService.startLearning(any())).thenReturn(startLearningResponse);
        when(bo.globalRelayService.signCert(any())).thenReturn(new RelaySignCertResponseDTO(CERTIFICATE));
        when(bo.controllerService.startRelaying(any())).thenReturn(startRelayingResponse);

        assertThrows(IllegalStateException.class, () -> bo.connectController(aggregator()));
    }
}
