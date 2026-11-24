package de.unihamburg.daibetes.api.feddbclient;

import bio.cosy.feddb.core.api.socket.FlowerSupernodeKeyRequestDTO;
import bio.cosy.feddb.core.execution.run.dto.AppMessageWrapperDTO;
import bio.cosy.feddb.core.execution.run.dto.FlowerSupernodeKeyDTO;
import bio.cosy.feddb.core.execution.run.dto.FlowerSupernodeRegisteredDTO;
import bio.cosy.feddb.core.execution.run.types.AppMessageTypeEnum;
import de.unihamburg.daibetes.api.project.training.federated.FederatedTrainingFlowerAggregatorBO;
import de.unihamburg.daibetes.execution.attempt.ExecutionAttemptWebsocketService;
import io.quarkus.websockets.next.OpenConnections;
import io.quarkus.websockets.next.WebSocketConnection;
import org.jboss.logging.Logger;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FlowerSupernodeRegistrationBOTest {

    private static final String CONNECTION = "connection-1";
    private static final String EXPERIMENT = "experiment-1";
    private static final String CLINIC = "clinic-a";
    private static final String NODE = "node-1";
    private static final String KEY = "ecdsa-sha2-nistp384 AAAA";
    private static final long AGGREGATOR = 42L;

    private WebSocketConnection aggregatorConnection;

    private FlowerSupernodeRegistrationBO registrationBO(boolean aggregatorConnected) {
        FlowerSupernodeRegistrationBO bo = new FlowerSupernodeRegistrationBO();
        bo.broadcastBO = mock(FLNetClientBroadcastBO.class);
        bo.flowerAggregatorBO = mock(FederatedTrainingFlowerAggregatorBO.class);
        bo.connections = mock(OpenConnections.class);
        bo.logger = Logger.getLogger(FlowerSupernodeRegistrationBOTest.class);
        when(bo.broadcastBO.isConnectionInExperiment(CONNECTION, EXPERIMENT)).thenReturn(true);
        when(bo.flowerAggregatorBO.findAggregatorAttemptIdTransactional(EXPERIMENT, NODE, CLINIC)).thenReturn(Optional.of(AGGREGATOR));
        aggregatorConnection = mock(WebSocketConnection.class);
        when(aggregatorConnection.pathParam("runId")).thenReturn(String.valueOf(AGGREGATOR));
        when(aggregatorConnection.pathParam("type")).thenReturn("app");
        when(bo.connections.findByEndpointId(ExecutionAttemptWebsocketService.class.getName()))
                .thenReturn(aggregatorConnected ? List.of(aggregatorConnection) : List.of());
        return bo;
    }

    private FlowerSupernodeKeyRequestDTO request() {
        return new FlowerSupernodeKeyRequestDTO("request-1", EXPERIMENT, CLINIC, NODE, KEY);
    }

    private FlowerSupernodeRegisteredDTO answerSentTo(FlowerSupernodeRegistrationBO bo, String connectionId) {
        ArgumentCaptor<FlowerSupernodeRegisteredDTO> answer = ArgumentCaptor.forClass(FlowerSupernodeRegisteredDTO.class);
        verify(bo.broadcastBO).sendFlowerSupernodeRegistered(eq(connectionId), answer.capture());
        return answer.getValue();
    }

    @Test
    @SuppressWarnings("unchecked")
    void forwardsTheKeyToTheStepsAggregatorNamedAfterTheClinic() {
        FlowerSupernodeRegistrationBO bo = registrationBO(true);

        bo.forward(request(), CONNECTION);

        ArgumentCaptor<AppMessageWrapperDTO<FlowerSupernodeKeyDTO>> sent = ArgumentCaptor.forClass(AppMessageWrapperDTO.class);
        verify(aggregatorConnection).sendTextAndAwait(sent.capture());
        assertEquals(AppMessageTypeEnum.FLOWER_SUPERNODE_KEY, sent.getValue().getType());
        assertEquals(new FlowerSupernodeKeyDTO("request-1", KEY, CLINIC), sent.getValue().getMessage());
        verify(bo.broadcastBO, never()).sendFlowerSupernodeRegistered(any(), any());
    }

    @Test
    void passesTheAggregatorsAnswerBackToTheClinicThatAsked() {
        FlowerSupernodeRegistrationBO bo = registrationBO(true);
        bo.forward(request(), CONNECTION);

        bo.answer(AGGREGATOR, new FlowerSupernodeRegisteredDTO("request-1", true, null));

        assertTrue(answerSentTo(bo, CONNECTION).isSuccess());
    }

    /** Only the aggregator that was asked may answer, and only once. */
    @Test
    void ignoresAnswersFromAnotherAttemptOrForUnknownRequests() {
        FlowerSupernodeRegistrationBO bo = registrationBO(true);
        bo.forward(request(), CONNECTION);

        bo.answer(7L, new FlowerSupernodeRegisteredDTO("request-1", true, null));
        bo.answer(AGGREGATOR, new FlowerSupernodeRegisteredDTO("unknown", true, null));

        verify(bo.broadcastBO, never()).sendFlowerSupernodeRegistered(any(), any());
    }

    @Test
    void answersNotReadyWhileTheAggregatorIsNotConnectedSoTheClinicAsksAgain() {
        FlowerSupernodeRegistrationBO bo = registrationBO(false);

        bo.forward(request(), CONNECTION);

        FlowerSupernodeRegisteredDTO answer = answerSentTo(bo, CONNECTION);
        assertFalse(answer.isSuccess());
        assertEquals(FlowerSupernodeRegistrationBO.ERROR_NOT_READY, answer.getError());
        assertEquals("request-1", answer.getRequestId());
    }

    @Test
    void refusesAConnectionThatIsNotPartOfTheExperiment() {
        FlowerSupernodeRegistrationBO bo = registrationBO(true);

        bo.forward(request(), "other-connection");

        assertEquals(FlowerSupernodeRegistrationBO.ERROR_REFUSED, answerSentTo(bo, "other-connection").getError());
        verify(aggregatorConnection, never()).sendTextAndAwait(any(Object.class));
    }

    @Test
    void refusesAClinicWithoutAFlowerStepAndAMissingKeyTheSameWay() {
        FlowerSupernodeRegistrationBO bo = registrationBO(true);
        when(bo.flowerAggregatorBO.findAggregatorAttemptIdTransactional(EXPERIMENT, NODE, CLINIC)).thenReturn(Optional.empty());

        bo.forward(request(), CONNECTION);

        assertEquals(FlowerSupernodeRegistrationBO.ERROR_REFUSED, answerSentTo(bo, CONNECTION).getError());
        verify(aggregatorConnection, never()).sendTextAndAwait(any(Object.class));
    }
}
