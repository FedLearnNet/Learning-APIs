package de.unihamburg.daibetes.api.project.training.federated;

import bio.cosy.feddb.core.api.app.CommunicationEngine;
import bio.cosy.feddb.core.execution.event.ExecutionEventPublisher;
import bio.cosy.feddb.core.execution.run.dto.FederatedRunConfigDTO;
import bio.cosy.feddb.core.execution.run.dto.FederatedRunEnricher;
import bio.cosy.feddb.core.execution.run.dto.StartRunDTO;
import bio.cosy.feddb.core.execution.run.types.RunStatusTypes;
import de.unihamburg.daibetes.api.workflow.node.WorkflowNodeEntity;
import de.unihamburg.daibetes.config.FLNetConfig;
import de.unihamburg.daibetes.execution.attempt.ExecutionAttemptAO;
import de.unihamburg.daibetes.execution.attempt.ExecutionAttemptEntity;
import de.unihamburg.daibetes.execution.step.ExecutionRunStepEntity;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.LinkedHashMap;
import java.util.Optional;
import java.util.UUID;

/**
 * The platform's aggregator for a Flower-based step: one more attempt of the step, without a clinic, that runs in a
 * container on the platform like any other attempt - unlike {@link FederatedTrainingAggregatorBO}, it never joins
 * the FL-Net relay or Controller. Flower's SuperNode&lt;-&gt;SuperLink connection is reached directly through the
 * SuperLink's own exposed port, authenticated at the application layer by Flower itself, so there is nothing to
 * register with the Controller and no CSR to sign - that's why this class is much smaller than the FL-Net one.
 */
@ApplicationScoped
public class FederatedTrainingFlowerAggregatorBO {

    @Inject
    ExecutionAttemptAO attemptAO;

    @Inject
    ExecutionEventPublisher events;

    @Inject
    FLNetConfig config;

    @Inject
    FlowerPlatformCertificateAO certificateAO;

    /** True for a workflow node built from a Flower-tagged tool. */
    public static boolean isFlowerNode(WorkflowNodeEntity node) {
        return node != null && node.getToolVersion() != null
                && node.getToolVersion().getFederatedApp().getCommunicationEngine() == CommunicationEngine.FLOWER;
    }

    public static boolean isFlowerStep(ExecutionRunStepEntity step) {
        return isFlowerNode(step.getWorkflowNode());
    }

    /**
     * The platform's own attempt of a Flower step - one of {@link FederatedTrainingAggregatorBO#isAggregator}, but
     * never carrying a relay config, which is what tells it apart from the FL-Net aggregator attempt.
     */
    public static boolean isFlowerAggregator(ExecutionAttemptEntity attempt) {
        return FederatedTrainingAggregatorBO.isAggregator(attempt) && attempt.getFederation().getRelayConfig() == null;
    }

    /** Adds the aggregator to the step; it starts once the step is committed, same as any other attempt. */
    public ExecutionAttemptEntity addAttempt(ExecutionRunStepEntity step, LinkedHashMap<String, Object> params) {
        ExecutionAttemptEntity attempt = new ExecutionAttemptEntity();
        attempt.setPublicId(UUID.randomUUID());
        attempt.setStep(step);
        attempt.setTrialIndex(step.getAttempts().size());
        attempt.setParams(new LinkedHashMap<>(params));
        attempt.setStatus(RunStatusTypes.PENDING);
        // No relay config at all - this is what distinguishes it from FederatedTrainingAggregatorBO's attempt.
        attempt.federationOrNew();
        step.getAttempts().add(attempt);
        // The aggregator holds the model the step trained.
        step.setSelectedAttempt(attempt);
        attemptAO.persist(attempt);
        events.attemptLaunch(attempt);
        Log.infof("Step %d runs the Flower aggregator on the platform as attempt %d", step.getId(), attempt.getId());
        return attempt;
    }

    /**
     * The platform holds no patient data, its aggregator only aggregates - it needs no SuperLink
     * address to reach, since it runs the SuperLink directly. It still needs to know its OWN
     * configured external address, though: the SuperLink's TLS leaf certificate must carry it as
     * a SAN entry, or a clinic's SuperNode will fail hostname verification against the address it
     * actually dials even with the right CA. Carried on the same {@code superlinkAddress} field a
     * clinic's config uses, re-used here as "my own address" rather than "the address to reach".
     * <p>
     * It also gets the platform's stored CA, certificate and key, if there is one: it signs this
     * run's SuperLink leaf certificate with it, so the SuperLink presents a certificate the run's
     * clinics - given the same stored certificate - already trust. Without one it generates a new
     * CA and reports it back (see ExecutionAttemptWebsocketService#onFlowerCaReported).
     */
    public void customizeStartup(ExecutionAttemptEntity attempt, StartRunDTO start) {
        start.setInputFilePaths(new LinkedHashMap<>());
        Optional<String> ownAddress = config.federatedLearning().flower().superlinkAddress();
        Optional<FlowerPlatformCertificateEntity> storedCa = certificateAO.findStored();
        if (ownAddress.isEmpty() && storedCa.isEmpty()) {
            FederatedRunEnricher.enrichWithFlower(start, true, null);
            return;
        }
        FederatedRunConfigDTO flowerConfig = new FederatedRunConfigDTO();
        flowerConfig.setSuperlinkAddress(ownAddress.orElse(null));
        storedCa.ifPresent(ca -> {
            flowerConfig.setCaCertificate(ca.getCaCertificate());
            flowerConfig.setCaPrivateKey(ca.getCaPrivateKey());
        });
        FederatedRunEnricher.enrichWithFlower(start, true, flowerConfig);
    }
}
