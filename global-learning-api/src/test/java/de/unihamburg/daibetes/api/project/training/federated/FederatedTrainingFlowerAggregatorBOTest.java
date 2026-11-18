package de.unihamburg.daibetes.api.project.training.federated;

import bio.cosy.feddb.core.api.app.CommunicationEngine;
import bio.cosy.feddb.core.execution.event.ExecutionEventPublisher;
import bio.cosy.feddb.core.execution.run.dto.StartRunDTO;
import de.unihamburg.daibetes.execution.attempt.ExecutionAttemptAO;
import de.unihamburg.daibetes.execution.attempt.ExecutionAttemptEntity;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class FederatedTrainingFlowerAggregatorBOTest {

    private FederatedTrainingFlowerAggregatorBO flowerAggregatorBO() {
        FederatedTrainingFlowerAggregatorBO bo = new FederatedTrainingFlowerAggregatorBO();
        bo.attemptAO = mock(ExecutionAttemptAO.class);
        bo.events = mock(ExecutionEventPublisher.class);
        return bo;
    }

    /**
     * The platform's own attempt runs the SuperLink directly, never joins an FL-Net relay, and
     * needs no SuperLink address of its own - unlike a clinic's attempt (see LearningSetupBOTest),
     * whose config carries where to reach it.
     */
    @Test
    void customizeStartupTagsTheStartAsFlowerAggregatorWithNoRelayConfig() {
        FederatedTrainingFlowerAggregatorBO bo = flowerAggregatorBO();
        ExecutionAttemptEntity attempt = new ExecutionAttemptEntity();
        StartRunDTO start = new StartRunDTO();

        bo.customizeStartup(attempt, start);

        assertEquals(CommunicationEngine.FLOWER, start.getCommunicationEngine());
        assertTrue(start.getInputFilePaths().isEmpty());
        assertEquals(1, start.getParticipants().size());
        assertEquals("AGGREGATOR", start.getParticipants().get(0).getRole());
        assertNull(start.getConfig());
    }
}
