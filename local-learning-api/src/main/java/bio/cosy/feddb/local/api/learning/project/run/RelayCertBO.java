package bio.cosy.feddb.local.api.learning.project.run;

import bio.cosy.feddb.core.api.socket.RelayCertSignRequestDTO;
import bio.cosy.feddb.core.api.socket.RelayCertSignResponseDTO;
import bio.cosy.feddb.core.execution.run.types.RunStatusTypes;
import bio.cosy.feddb.core.services.controller.ControllerStartRelayingRequestDTO;
import bio.cosy.feddb.local.api.eam.WebsocketSender;
import bio.cosy.feddb.local.api.learning.project.FederatedLearningProjectAO;
import bio.cosy.feddb.local.config.FLNetClientConfig;
import bio.cosy.feddb.local.execution.federation.ExecutionFederationAO;
import bio.cosy.feddb.local.execution.federation.ExecutionFederationEntity;
import bio.cosy.feddb.local.execution.run.ExecutionRunEntity;
import bio.cosy.feddb.local.execution.step.ExecutionRunStepAO;
import bio.cosy.feddb.local.execution.step.ExecutionRunStepEntity;
import bio.cosy.feddb.local.services.controller.LocalControllerLearningService;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.LockModeType;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Gets the relay certificate for the controller run of a step (mTLS towards the relay server). The controller
 * creates a private key and a certificate signing request when its run starts and only joins the relay once it has
 * the signed certificate. Only the platform reaches the relay server, so the request goes there through the
 * websocket and the certificate comes back the same way. The private key never leaves the controller.
 */
@ApplicationScoped
public class RelayCertBO {

    @Inject
    ExecutionFederationAO federationAO;

    @Inject
    ExecutionRunStepAO stepAO;

    @Inject
    FederatedLearningProjectAO projectAO;

    @Inject
    FederatedLearningRunBO federatedLearningRunBO;

    @Inject
    WebsocketSender websocketSender;

    @Inject
    FLNetClientConfig config;

    @Inject
    @RestClient
    LocalControllerLearningService controllerService;

    /** Stores the request of the step's controller run and asks the platform to have it signed. */
    public void requestCertificate(ExecutionFederationEntity federation, String csr) {
        federation.setRelayCsr(csr);
        federation.setRelayCertRequestedAt(Instant.now());
        federation.setRelayCertAttempts(1);
        federation.setRelayCertSigned(false);
        federation.setRelayStopped(false);
        Log.infof("Requesting relay certificate for step %d", federation.getStep().getId());
        send(federation);
    }

    /**
     * Hands the signed certificate to the controller, which then joins the relay. The step fails if signing was
     * refused or the controller cannot join.
     */
    @Transactional
    public void handleSigned(RelayCertSignResponseDTO response) {
        Optional<ExecutionRunStepEntity> found = projectAO.findByRequestIdOptional(response.getGlobalUniqueExperimentId())
                .map(project -> project.getRun())
                .filter(run -> run != null && run.getParticipantId().equals(response.getUniqueRandomClinicId()))
                .flatMap(run -> stepAO.findByNodeId(run.getId(), response.getCurrentNodeId()));
        // Locked, so of two answers to a retried request only the first is used.
        ExecutionFederationEntity federation = found.map(ExecutionRunStepEntity::getFederation)
                .flatMap(linked -> federationAO.findByIdOptional(linked.getId(), LockModeType.PESSIMISTIC_WRITE))
                .orElse(null);
        if (federation == null || federation.getConfig() == null
                || !Objects.equals(federation.getConfig().getClientId(), response.getClientId())) {
            Log.warnf("Ignoring relay certificate of node %s of training %s, it is not for this clinic",
                    response.getCurrentNodeId(), response.getGlobalUniqueExperimentId());
            return;
        }
        ExecutionRunStepEntity step = federation.getStep();
        if (step.hasEnded() || Boolean.TRUE.equals(federation.getRelayStopped())
                || federation.getRelayCsr() == null || Boolean.TRUE.equals(federation.getRelayCertSigned())) {
            Log.infof("Ignoring relay certificate for step %d, it does not wait for one", step.getId());
            return;
        }
        federation.setRelayCertSigned(true);
        if (response.getCertificate() == null || response.getCertificate().isBlank()) {
            federatedLearningRunBO.fail(step, "Relay certificate was not signed: " + response.getError());
            return;
        }
        // The app key of a controller run is the relay client id.
        ControllerStartRelayingRequestDTO request = new ControllerStartRelayingRequestDTO(federation.getConfig().getChannel(),
                federation.getConfig().getClientId(), response.getCertificate());
        String error = null;
        try (Response controllerResponse = controllerService.startRelaying(request)) {
            if (controllerResponse.getStatus() != Response.Status.OK.getStatusCode()) {
                error = "status " + controllerResponse.getStatus();
            }
        } catch (WebApplicationException e) {
            error = "status " + e.getResponse().getStatus();
        } catch (RuntimeException e) {
            Log.errorf(e, "Could not hand the relay certificate to the controller of step %d", step.getId());
            error = e.getMessage();
        }
        if (error != null) {
            federatedLearningRunBO.fail(step, "Controller could not connect to the relay server: " + error);
            return;
        }
        Log.infof("Controller of step %d joined the relay", step.getId());
    }

    /** Sends a request again if its certificate did not arrive in time, and fails the step if it never does. */
    @Transactional
    public void retryPending() {
        retryPending(Instant.now());
    }

    void retryPending(Instant now) {
        long timeoutMillis = config.relay().certSign().timeout().toMillis();
        int maxAttempts = config.relay().certSign().maxAttempts();
        for (ExecutionFederationEntity pending : federationAO.listPendingRelayCerts()) {
            Instant requestedAt = pending.getRelayCertRequestedAt();
            if (requestedAt != null && now.toEpochMilli() - requestedAt.toEpochMilli() < timeoutMillis) {
                continue;
            }
            int attempts = pending.getRelayCertAttempts() != null ? pending.getRelayCertAttempts() : 0;
            if (attempts >= maxAttempts) {
                // A late answer must not connect the controller of a failed step.
                pending.setRelayCertSigned(true);
                federatedLearningRunBO.fail(pending.getStep(), "Relay certificate was not signed after " + attempts + " attempts");
                continue;
            }
            Log.warnf("No relay certificate for step %d yet, sending request %d of %d", pending.getStep().getId(), attempts + 1, maxAttempts);
            pending.setRelayCertAttempts(attempts + 1);
            pending.setRelayCertRequestedAt(now);
            send(pending);
        }
    }

    private void send(ExecutionFederationEntity federation) {
        ExecutionRunStepEntity step = federation.getStep();
        ExecutionRunEntity run = step.getRun();
        String globalId = projectAO.find("run.id", run.getId()).firstResult().getRequest().getGlobalFLExperimentUniqueId();
        websocketSender.sendRelayCertRequest(new RelayCertSignRequestDTO(globalId, run.getParticipantId(),
                step.getWorkflowNode().getNodeId(), federation.getConfig().getClientId(), federation.getRelayCsr()));
    }
}
