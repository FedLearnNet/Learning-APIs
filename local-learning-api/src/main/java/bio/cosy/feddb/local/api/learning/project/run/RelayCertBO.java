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
import io.quarkus.logging.Log;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.util.Date;
import java.util.Optional;

/**
 * Gets the relay certificate for the controller run of a workflow step (mTLS towards the relay server).
 * The controller creates a private key and a certificate signing request (CSR) when a run is started and only
 * connects to the relay server once it received the signed certificate. Only the global server can reach the
 * relay server, so the CSR is sent there via the websocket and the certificate comes back the same way.
 * The private key never leaves the controller.
 */
@ApplicationScoped
public class RelayCertBO {

    @Inject
    FederatedLearningExperimentStepBO stepBO;

    @Inject
    FederatedLearningExperimentStepAO stepAO;

    @Inject
    FederatedLearningExperimentAO experimentAO;

    @Inject
    WebsocketSender websocketSender;

    @Inject
    FLNetClientConfig config;

    @Inject
    @RestClient
    LocalControllerLearningService controllerService;

    /**
     * Stores the CSR of the step's controller run and asks the global server to have it signed.
     */
    public void requestCertificate(final Long stepId, final String csr) {
        RelayCertRequest request = stepBO.startRelayCertRequest(stepId, csr);
        Log.infof("Requesting relay certificate for step %d", stepId);
        send(request);
    }

    /**
     * Handles the answer of the global server: passes the signed certificate on to the controller, which then
     * connects to the relay server. The step fails if signing was refused or the controller cannot connect.
     */
    public void handleSigned(final RelayCertSignResponseDTO response) {
        Optional<FederatedLearningExperimentEntity> experiment = experimentAO.getByGlobalRequestId(response.getGlobalUniqueExperimentId());
        if (experiment.isEmpty() || !experiment.get().getUniqueRandomClinicId().equals(response.getUniqueRandomClinicId())) {
            Log.warnf("Ignoring relay certificate for unknown experiment %s or another clinic", response.getGlobalUniqueExperimentId());
            return;
        }
        Optional<FederatedLearningExperimentStepEntity> stepOptional = stepAO.findByWorkflowNodeId(experiment.get().getId(), response.getCurrentNodeId());
        if (stepOptional.isEmpty()) {
            Log.warnf("Ignoring relay certificate for unknown node %s of experiment %s", response.getCurrentNodeId(), response.getGlobalUniqueExperimentId());
            return;
        }
        FederatedLearningExperimentStepEntity step = stepOptional.get();
        FederatedLearningRelayInfoDTO relay = step.getRelayInfo();
        if (relay == null || !relay.getId().equals(response.getClientId())) {
            Log.warnf("Ignoring relay certificate for step %d: issued for another relay client", step.getId());
            return;
        }
        if (RunStatusTypes.isFinalStatus(step.getStepStatus()) || Boolean.TRUE.equals(step.getRelayStopped())) {
            Log.infof("Ignoring relay certificate for step %d: step is not running anymore", step.getId());
            return;
        }
        // Only the first answer is used, the answer to a retried request may arrive as well
        if (!stepAO.claimRelayCertSignedTransactional(step.getId())) {
            Log.infof("Ignoring relay certificate for step %d: not waiting for a certificate", step.getId());
            return;
        }

        if (response.getCertificate() == null || response.getCertificate().isBlank()) {
            failStep(step.getId(), "Relay certificate was not signed: " + response.getError());
            return;
        }
        // The app key of a controller run is the relay client id, see FederatedLearningExperimentMapper.relayToController
        ControllerStartRelayingRequestDTO request = new ControllerStartRelayingRequestDTO(relay.getChannel(), relay.getId(), response.getCertificate());
        try (Response controllerResponse = controllerService.startRelaying(request)) {
            if (controllerResponse.getStatus() != Response.Status.OK.getStatusCode()) {
                failStep(step.getId(), "Controller could not connect to the relay server: status " + controllerResponse.getStatus());
                return;
            }
        } catch (WebApplicationException e) {
            failStep(step.getId(), "Controller could not connect to the relay server: status " + e.getResponse().getStatus());
            return;
        } catch (Exception e) {
            Log.errorf(e, "Error passing the relay certificate to the controller for step %d", step.getId());
            failStep(step.getId(), "Controller could not connect to the relay server: " + e.getMessage());
            return;
        }
        Log.infof("Controller of step %d connected to the relay server", step.getId());
    }

    /**
     * Sends the CSR again if the certificate did not arrive in time, and fails the step if it never does.
     */
    @Scheduled(every = "30s", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void retryPending() {
        retryPending(new Date());
    }

    void retryPending(final Date now) {
        long timeoutMillis = config.relay().certSign().timeout().toMillis();
        int maxAttempts = config.relay().certSign().maxAttempts();
        for (RelayCertRequest pending : stepBO.findPendingRelayCerts()) {
            if (pending.requestedAt() != null && now.getTime() - pending.requestedAt().toEpochMilli() < timeoutMillis) {
                continue;
            }
            try {
                if (pending.attempts() >= maxAttempts) {
                    // claim it so a late answer does not connect a controller of a failed step
                    if (stepAO.claimRelayCertSignedTransactional(pending.stepId())) {
                        failStep(pending.stepId(), "Relay certificate was not signed after " + pending.attempts() + " attempts");
                    }
                    continue;
                }
                Log.warnf("No relay certificate for step %d yet, sending request %d of %d",
                        pending.stepId(), pending.attempts() + 1, maxAttempts);
                stepAO.retryRelayCertRequestTransactional(pending.stepId());
                send(pending);
            } catch (Exception e) {
                Log.errorf(e, "Error retrying the relay certificate request of step %d", pending.stepId());
            }
        }
    }

    private void send(final RelayCertRequest request) {
        websocketSender.sendRelayCertRequest(new RelayCertSignRequestDTO(
                request.globalUniqueExperimentId(),
                request.uniqueRandomClinicId(),
                request.nodeId(),
                request.clientId(),
                request.csr()
        ));
    }

    /**
     * Fails the step like an error reported by the app: the global server is informed and stops the experiment.
     */
    private void failStep(final Long stepId, final String error) {
        Log.errorf("Failing step %d: %s", stepId, error);
        FederatedLearningExperimentStepDTO step = stepBO.getById(stepId);
        step.setStepStatus(RunStatusTypes.ERROR);
        step.setLastError(error);
        stepBO.updateStatus(step);
    }
}
