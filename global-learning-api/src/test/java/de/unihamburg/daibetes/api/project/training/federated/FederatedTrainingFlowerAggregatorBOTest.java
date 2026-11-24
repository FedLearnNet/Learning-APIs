package de.unihamburg.daibetes.api.project.training.federated;

import bio.cosy.feddb.core.api.app.CommunicationEngine;
import bio.cosy.feddb.core.execution.event.ExecutionEventPublisher;
import bio.cosy.feddb.core.execution.run.dto.FederatedRunConfigDTO;
import bio.cosy.feddb.core.execution.run.dto.StartRunDTO;
import de.unihamburg.daibetes.config.FLNetConfig;
import de.unihamburg.daibetes.execution.attempt.ExecutionAttemptAO;
import de.unihamburg.daibetes.execution.attempt.ExecutionAttemptEntity;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FederatedTrainingFlowerAggregatorBOTest {

    private FLNetConfig.FederatedLearningConfig federatedLearningConfig;

    private FederatedTrainingFlowerAggregatorBO flowerAggregatorBO(Optional<String> ownSuperlinkAddress) {
        return flowerAggregatorBO(ownSuperlinkAddress, Optional.empty());
    }

    private FederatedTrainingFlowerAggregatorBO flowerAggregatorBO(Optional<String> ownSuperlinkAddress,
                                                                    Optional<FlowerPlatformCertificateEntity> storedCa) {
        FederatedTrainingFlowerAggregatorBO bo = new FederatedTrainingFlowerAggregatorBO();
        bo.attemptAO = mock(ExecutionAttemptAO.class);
        bo.events = mock(ExecutionEventPublisher.class);
        bo.config = mock(FLNetConfig.class);
        federatedLearningConfig = mock(FLNetConfig.FederatedLearningConfig.class);
        FLNetConfig.FlowerConfig flowerConfig = mock(FLNetConfig.FlowerConfig.class);
        when(bo.config.federatedLearning()).thenReturn(federatedLearningConfig);
        when(federatedLearningConfig.flower()).thenReturn(flowerConfig);
        when(flowerConfig.superlinkAddress()).thenReturn(ownSuperlinkAddress);
        bo.certificateAO = mock(FlowerPlatformCertificateAO.class);
        when(bo.certificateAO.findStored()).thenReturn(storedCa);
        return bo;
    }

    /**
     * The platform's own attempt runs the SuperLink directly, never joins an FL-Net relay, and
     * needs no FL-Net relay config of its own - unlike a clinic's attempt (see LearningSetupBOTest),
     * whose config carries where to reach it. When the platform's own SuperLink address isn't
     * configured yet (nothing deployed it to a reachable address), the config stays null.
     */
    @Test
    void customizeStartupTagsTheStartAsFlowerAggregatorWithNoRelayConfig() {
        FederatedTrainingFlowerAggregatorBO bo = flowerAggregatorBO(Optional.empty());
        ExecutionAttemptEntity attempt = new ExecutionAttemptEntity();
        StartRunDTO start = new StartRunDTO();

        bo.customizeStartup(attempt, start);

        assertEquals(CommunicationEngine.FLOWER, start.getCommunicationEngine());
        assertTrue(start.getInputFilePaths().isEmpty());
        assertEquals(1, start.getParticipants().size());
        assertEquals("AGGREGATOR", start.getParticipants().get(0).getRole());
        assertNull(start.getConfig());
    }

    /**
     * Once the platform's own SuperLink address is configured, the aggregator's own start carries
     * it - not to reach anything, but so the app (pyfedappwrap) can put it in the SuperLink's own
     * TLS leaf certificate SAN, or a clinic's SuperNode fails hostname verification against the
     * address it actually dials.
     */
    @Test
    void customizeStartupCarriesThePlatformsOwnSuperlinkAddressOnceConfigured() {
        FederatedTrainingFlowerAggregatorBO bo = flowerAggregatorBO(Optional.of("flower.platform.example:8000"));
        ExecutionAttemptEntity attempt = new ExecutionAttemptEntity();
        StartRunDTO start = new StartRunDTO();

        bo.customizeStartup(attempt, start);

        assertEquals("flower.platform.example:8000", start.getConfig().getSuperlinkAddress());
    }

    /**
     * Once the platform has a stored CA, its aggregator gets the certificate and key back on every
     * run, so it signs that run's SuperLink leaf with the same CA the run's clinics were given
     * instead of generating a new one the clinics don't trust.
     */
    @Test
    void customizeStartupHandsTheStoredCaBackToTheAggregator() {
        FlowerPlatformCertificateEntity storedCa = new FlowerPlatformCertificateEntity();
        storedCa.setCaCertificate("ca-cert-pem");
        storedCa.setCaPrivateKey("ca-key-pem");
        FederatedTrainingFlowerAggregatorBO bo = flowerAggregatorBO(Optional.of("flower.platform.example:8000"),
                Optional.of(storedCa));
        StartRunDTO start = new StartRunDTO();

        bo.customizeStartup(new ExecutionAttemptEntity(), start);

        assertEquals("ca-cert-pem", start.getConfig().getCaCertificate());
        assertEquals("ca-key-pem", start.getConfig().getCaPrivateKey());
        assertEquals("flower.platform.example:8000", start.getConfig().getSuperlinkAddress());
    }

    /** The CA private key must never show up in logs, even when the whole config is logged. */
    @Test
    void theCaPrivateKeyIsLeftOutOfToString() {
        FederatedRunConfigDTO dto = new FederatedRunConfigDTO();
        dto.setCaPrivateKey("ca-key-pem");

        assertFalse(dto.toString().contains("ca-key-pem"));
    }
}
