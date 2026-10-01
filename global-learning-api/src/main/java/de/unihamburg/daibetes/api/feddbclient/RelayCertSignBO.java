package de.unihamburg.daibetes.api.feddbclient;

import bio.cosy.feddb.core.api.socket.RelayCertSignRequestDTO;
import bio.cosy.feddb.core.api.socket.RelayCertSignResponseDTO;
import bio.cosy.feddb.core.services.controller.RelaySignCertRequestDTO;
import bio.cosy.feddb.core.services.controller.RelaySignCertResponseDTO;
import de.unihamburg.daibetes.api.project.experiment.federated.step.ProjectFederatedExperimentStepBO;
import de.unihamburg.daibetes.services.GlobalRelayService;
import io.quarkus.arc.log.LoggerName;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

import java.util.Optional;

/**
 * Lets the relay server sign the certificate a client's controller needs to connect to the relay server (mTLS).
 * The private key stays on the controller: the client sends a certificate signing request (CSR) via the websocket
 * and gets the signed certificate back.
 */
@ApplicationScoped
public class RelayCertSignBO {

    // Same answer for every refusal, so a client cannot probe which experiments, clinics or client ids exist
    static final String ERROR_REFUSED = "Signing the relay certificate was refused";
    static final String ERROR_FAILED = "Signing the relay certificate failed";

    @Inject
    FLNetClientBroadcastBO broadcastBO;

    @Inject
    ProjectFederatedExperimentStepBO stepBO;

    @Inject
    @RestClient
    GlobalRelayService globalRelayService;

    @LoggerName("RelayCertSign")
    Logger logger;

    /**
     * Signs the CSR if the connection takes part in the experiment and the relay client id is the one that was
     * assigned to the clinic for the relay run of the requested workflow step.
     */
    public RelayCertSignResponseDTO sign(RelayCertSignRequestDTO request, String connectionId) {
        if (request == null || request.getCsr() == null || request.getCsr().isBlank()) {
            logger.warnf("Refused relay certificate request without CSR from connection %s", connectionId);
            return RelayCertSignResponseDTO.createErrorResponse(request == null ? new RelayCertSignRequestDTO() : request, ERROR_REFUSED);
        }
        if (!broadcastBO.isConnectionInExperiment(connectionId, request.getGlobalUniqueExperimentId())) {
            logger.warnf("Refused relay certificate request from connection %s: not registered for experiment %s",
                    connectionId, request.getGlobalUniqueExperimentId());
            return RelayCertSignResponseDTO.createErrorResponse(request, ERROR_REFUSED);
        }
        Optional<String> channel = stepBO.findRelayChannelForClient(
                request.getGlobalUniqueExperimentId(),
                request.getCurrentNodeId(),
                request.getUniqueRandomClinicId(),
                request.getClientId()
        );
        if (channel.isEmpty()) {
            logger.warnf("Refused relay certificate request from connection %s: clinic %s does not own relay client %s for node %s of experiment %s",
                    connectionId, request.getUniqueRandomClinicId(), request.getClientId(), request.getCurrentNodeId(), request.getGlobalUniqueExperimentId());
            return RelayCertSignResponseDTO.createErrorResponse(request, ERROR_REFUSED);
        }

        try {
            RelaySignCertResponseDTO signed = globalRelayService.signCert(
                    new RelaySignCertRequestDTO(channel.get(), request.getClientId(), request.getCsr()));
            if (signed == null || signed.getCertificate() == null || signed.getCertificate().isBlank()) {
                logger.errorf("Relay server returned no certificate for relay client %s", request.getClientId());
                return RelayCertSignResponseDTO.createErrorResponse(request, ERROR_FAILED);
            }
            logger.infof("Signed relay certificate for relay client %s of experiment %s", request.getClientId(), request.getGlobalUniqueExperimentId());
            return RelayCertSignResponseDTO.createResponse(request, signed.getCertificate());
        } catch (Exception e) {
            logger.errorf(e, "Relay server did not sign the certificate for relay client %s", request.getClientId());
            return RelayCertSignResponseDTO.createErrorResponse(request, ERROR_FAILED);
        }
    }
}
