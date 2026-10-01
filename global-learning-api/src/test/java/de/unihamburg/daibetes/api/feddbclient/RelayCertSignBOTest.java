package de.unihamburg.daibetes.api.feddbclient;

import bio.cosy.feddb.core.api.socket.RelayCertSignRequestDTO;
import bio.cosy.feddb.core.api.socket.RelayCertSignResponseDTO;
import bio.cosy.feddb.core.services.controller.RelaySignCertRequestDTO;
import bio.cosy.feddb.core.services.controller.RelaySignCertResponseDTO;
import de.unihamburg.daibetes.api.project.experiment.federated.step.ProjectFederatedExperimentStepBO;
import de.unihamburg.daibetes.services.GlobalRelayService;
import jakarta.ws.rs.WebApplicationException;
import org.jboss.logging.Logger;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RelayCertSignBOTest {

    private static final String CONNECTION = "connection-1";
    private static final String EXPERIMENT = "experiment-1";
    private static final String CLINIC = "clinic-a";
    private static final String NODE = "node-1";
    private static final String CLIENT = "0a0b0c0d0e0f0a0b";
    private static final String CSR = "-----BEGIN CERTIFICATE REQUEST-----";

    private RelayCertSignBO signBO() {
        RelayCertSignBO bo = new RelayCertSignBO();
        bo.broadcastBO = mock(FLNetClientBroadcastBO.class);
        bo.stepBO = mock(ProjectFederatedExperimentStepBO.class);
        bo.globalRelayService = mock(GlobalRelayService.class);
        bo.logger = Logger.getLogger(RelayCertSignBOTest.class);
        when(bo.broadcastBO.isConnectionInExperiment(CONNECTION, EXPERIMENT)).thenReturn(true);
        when(bo.stepBO.findRelayChannelForClient(EXPERIMENT, NODE, CLINIC, CLIENT)).thenReturn(Optional.of("channel-1"));
        when(bo.globalRelayService.signCert(new RelaySignCertRequestDTO("channel-1", CLIENT, CSR)))
                .thenReturn(new RelaySignCertResponseDTO("signed-cert"));
        return bo;
    }

    private RelayCertSignRequestDTO request() {
        return new RelayCertSignRequestDTO(EXPERIMENT, CLINIC, NODE, CLIENT, CSR);
    }

    @Test
    void signsCsrOfTheClientAssignedToTheClinic() {
        RelayCertSignResponseDTO response = signBO().sign(request(), CONNECTION);

        assertEquals("signed-cert", response.getCertificate());
        assertNull(response.getError());
        assertEquals(EXPERIMENT, response.getGlobalUniqueExperimentId());
        assertEquals(CLINIC, response.getUniqueRandomClinicId());
        assertEquals(NODE, response.getCurrentNodeId());
        assertEquals(CLIENT, response.getClientId());
    }

    @Test
    void refusesConnectionThatIsNotRegisteredForTheExperiment() {
        RelayCertSignBO bo = signBO();

        RelayCertSignResponseDTO response = bo.sign(request(), "other-connection");

        assertNull(response.getCertificate());
        assertEquals(RelayCertSignBO.ERROR_REFUSED, response.getError());
        verify(bo.globalRelayService, never()).signCert(any());
    }

    @Test
    void refusesClientIdThatWasNotAssignedToTheClinic() {
        RelayCertSignBO bo = signBO();
        RelayCertSignRequestDTO request = request();
        // e.g. the relay client id of another clinic, another step or an unknown clinic
        request.setClientId("ffffffffffffffff");
        when(bo.stepBO.findRelayChannelForClient(EXPERIMENT, NODE, CLINIC, "ffffffffffffffff")).thenReturn(Optional.empty());

        RelayCertSignResponseDTO response = bo.sign(request, CONNECTION);

        assertNull(response.getCertificate());
        assertEquals(RelayCertSignBO.ERROR_REFUSED, response.getError());
        verify(bo.globalRelayService, never()).signCert(any());
    }

    @Test
    void refusesRequestWithoutCsr() {
        RelayCertSignBO bo = signBO();
        RelayCertSignRequestDTO request = request();
        request.setCsr(" ");

        assertEquals(RelayCertSignBO.ERROR_REFUSED, bo.sign(request, CONNECTION).getError());
        assertEquals(RelayCertSignBO.ERROR_REFUSED, bo.sign(null, CONNECTION).getError());
        verify(bo.globalRelayService, never()).signCert(any());
    }

    @Test
    void relayErrorIsReportedToTheClient() {
        RelayCertSignBO bo = signBO();
        // e.g. 409: the relay already signed another key for this client
        when(bo.globalRelayService.signCert(any())).thenThrow(new WebApplicationException(409));

        RelayCertSignResponseDTO response = bo.sign(request(), CONNECTION);

        assertNull(response.getCertificate());
        assertEquals(RelayCertSignBO.ERROR_FAILED, response.getError());
    }

    @Test
    void missingCertificateInRelayAnswerIsAnError() {
        RelayCertSignBO bo = signBO();
        when(bo.globalRelayService.signCert(any())).thenReturn(new RelaySignCertResponseDTO(null));

        assertEquals(RelayCertSignBO.ERROR_FAILED, bo.sign(request(), CONNECTION).getError());
    }
}
