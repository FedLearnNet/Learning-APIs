package de.unihamburg.daibetes.api.project.training.federated;

import bio.cosy.feddb.core.api.model.ModelSubDTO;
import bio.cosy.feddb.core.api.model.ModelSubDataDTO;
import bio.cosy.feddb.core.api.socket.FederatedLearningRelayInfoDTO;
import bio.cosy.feddb.core.execution.event.ExecutionEventPublisher;
import bio.cosy.feddb.core.execution.run.dto.FederatedRunConfigDTO;
import bio.cosy.feddb.core.execution.run.dto.FederatedRunEnricher;
import bio.cosy.feddb.core.execution.run.dto.StartRunDTO;
import bio.cosy.feddb.core.execution.run.types.RunStatusTypes;
import bio.cosy.feddb.core.services.controller.ControllerStartLearningRequestDTO;
import bio.cosy.feddb.core.services.controller.ControllerStartLearningResponseDTO;
import bio.cosy.feddb.core.services.controller.ControllerStartRelayingRequestDTO;
import bio.cosy.feddb.core.services.controller.RelaySignCertRequestDTO;
import bio.cosy.feddb.core.services.controller.RelaySignCertResponseDTO;
import de.unihamburg.daibetes.api.app.version.FederatedAppVersionEntity;
import de.unihamburg.daibetes.api.model.sub.ModelSubBO;
import de.unihamburg.daibetes.api.model.sub.result.ModelFederatedExperimentResultBO;
import de.unihamburg.daibetes.execution.attempt.ExecutionAttemptAO;
import de.unihamburg.daibetes.execution.attempt.ExecutionAttemptEntity;
import de.unihamburg.daibetes.execution.step.ExecutionRunStepEntity;
import de.unihamburg.daibetes.services.GlobalRelayService;
import de.unihamburg.daibetes.services.LocalControllerLearningService;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.util.LinkedHashMap;
import java.util.Optional;
import java.util.UUID;

/**
 * The aggregator of a training across clinics when the platform coordinates it instead of a clinic: one more attempt
 * of the federated step, without a clinic, that runs in a container on the platform like any other attempt. Its
 * controller joins the relay before the app starts; the step finishes once the clinics and the aggregator finished.
 */
@ApplicationScoped
public class FederatedTrainingAggregatorBO {

    @Inject
    ExecutionAttemptAO attemptAO;

    @Inject
    ExecutionEventPublisher events;

    @Inject
    ModelFederatedExperimentResultBO modelResultBO;

    @Inject
    ModelSubBO modelSubBO;

    @Inject
    @RestClient
    LocalControllerLearningService controllerService;

    @Inject
    @RestClient
    GlobalRelayService globalRelayService;

    /** The attempt running the platform's aggregator in the step; none when a clinic coordinates. */
    public static Optional<ExecutionAttemptEntity> aggregatorOf(ExecutionRunStepEntity step) {
        return step.getAttempts().stream().filter(FederatedTrainingAggregatorBO::isAggregator).findFirst();
    }

    public static boolean isAggregator(ExecutionAttemptEntity attempt) {
        return attempt.getFederation() != null && attempt.getFederation().getRelayConfig() != null;
    }

    /** Adds the aggregator to the step with the coordinator slot of the relay; it starts once the step is committed. */
    public ExecutionAttemptEntity addAttempt(ExecutionRunStepEntity step, FederatedLearningRelayInfoDTO slot,
                                             LinkedHashMap<String, Object> params) {
        ExecutionAttemptEntity attempt = new ExecutionAttemptEntity();
        attempt.setPublicId(UUID.randomUUID());
        attempt.setStep(step);
        attempt.setTrialIndex(step.getAttempts().size());
        attempt.setParams(new LinkedHashMap<>(params));
        attempt.setStatus(RunStatusTypes.PENDING);
        attempt.federationOrNew().setRelayConfig(slot.getRunConfig());
        step.getAttempts().add(attempt);
        // The aggregator holds the model the step trained.
        step.setSelectedAttempt(attempt);
        attemptAO.persist(attempt);
        events.attemptLaunch(attempt);
        Log.infof("Step %d runs the aggregator on the platform as attempt %d", step.getId(), attempt.getId());
        return attempt;
    }

    /**
     * Registers the aggregator's run with the platform's controller and joins it to the relay. The platform is the
     * global server, so it has the request signed by the relay server itself instead of through a clinic's websocket.
     */
    public void connectController(ExecutionAttemptEntity attempt) {
        FederatedRunConfigDTO relay = attempt.getFederation().getRelayConfig();
        String csr;
        try (Response response = controllerService.startLearning(new ControllerStartLearningRequestDTO(relay, attempt.getId()))) {
            if (response.getStatus() != Response.Status.OK.getStatusCode()) {
                throw new IllegalStateException("controller answered " + response.getStatus() + ": " + response.readEntity(String.class));
            }
            ControllerStartLearningResponseDTO started = response.readEntity(ControllerStartLearningResponseDTO.class);
            csr = started != null ? started.getCsr() : null;
        }
        if (csr == null || csr.isBlank()) {
            throw new IllegalStateException("controller returned no certificate signing request");
        }
        RelaySignCertResponseDTO signed = globalRelayService.signCert(new RelaySignCertRequestDTO(relay.getChannel(), relay.getClientId(), csr));
        if (signed == null || signed.getCertificate() == null || signed.getCertificate().isBlank()) {
            throw new IllegalStateException("relay server returned no certificate for attempt " + attempt.getId());
        }
        try (Response response = controllerService.startRelaying(
                new ControllerStartRelayingRequestDTO(relay.getChannel(), relay.getClientId(), signed.getCertificate()))) {
            if (response.getStatus() != Response.Status.OK.getStatusCode()) {
                throw new IllegalStateException("controller answered " + response.getStatus() + " on start-relaying: "
                        + response.readEntity(String.class));
            }
        }
        Log.infof("Controller of aggregator attempt %d joined the relay", attempt.getId());
    }

    /** The platform holds no patient data, its aggregator only aggregates. */
    public void customizeStartup(ExecutionAttemptEntity attempt, StartRunDTO start) {
        start.setInputFilePaths(new LinkedHashMap<>());
        FederatedRunEnricher.enrichWithFederatedRelay(start, attempt.getFederation().getRelayConfig(), true, true);
    }

    /**
     * Stores the model the aggregator uploaded like the one a coordinating clinic hands over. A private model is
     * stored too; publishing it is refused.
     */
    public void storeModel(ExecutionAttemptEntity attempt, ModelSubDataDTO model) {
        ExecutionRunStepEntity step = attempt.getStep();
        FederatedAppVersionEntity version = step.getWorkflowNode().getToolVersion();
        if (version == null) {
            throw new IllegalStateException("Step " + step.getId() + " has no app version to keep the model for");
        }
        ModelSubDTO sub = modelResultBO.createModel(version.getFederatedApp().getId(), version.getId(), step.getRun().getExperiment().getId());
        modelSubBO.addModelSubData(sub, model, step.getRun().getKeycloakId());
        Log.infof("Stored the model of aggregator attempt %d", attempt.getId());
    }
}
