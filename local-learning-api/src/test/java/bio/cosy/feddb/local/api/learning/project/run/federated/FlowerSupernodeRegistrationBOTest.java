package bio.cosy.feddb.local.api.learning.project.run.federated;

import bio.cosy.feddb.core.api.socket.FlowerSupernodeKeyRequestDTO;
import bio.cosy.feddb.core.execution.run.dto.AppMessageWrapperDTO;
import bio.cosy.feddb.core.execution.run.dto.FlowerSupernodeKeyDTO;
import bio.cosy.feddb.core.execution.run.dto.FlowerSupernodeRegisteredDTO;
import bio.cosy.feddb.core.execution.run.types.AppMessageTypeEnum;
import bio.cosy.feddb.local.api.eam.WebsocketSender;
import bio.cosy.feddb.local.api.learning.project.LearningProjectAO;
import bio.cosy.feddb.local.api.learning.project.LearningProjectEntity;
import bio.cosy.feddb.local.api.learning.request.LearningRequestEntity;
import bio.cosy.feddb.local.api.workflow.node.WorkflowNodeEntity;
import bio.cosy.feddb.local.execution.attempt.ExecutionAttemptAO;
import bio.cosy.feddb.local.execution.attempt.ExecutionAttemptEntity;
import bio.cosy.feddb.local.execution.run.ExecutionRunBO;
import bio.cosy.feddb.local.execution.run.ExecutionRunEntity;
import bio.cosy.feddb.local.execution.step.ExecutionRunStepEntity;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FlowerSupernodeRegistrationBOTest {

    private static final long ATTEMPT_ID = 11L;
    private static final long RUN_ID = 3L;
    private static final String EXPERIMENT = "experiment-1";
    private static final String CLINIC = "clinic-a";
    private static final String NODE = "node-1";
    private static final String KEY = "ecdsa-sha2-nistp384 AAAA";

    private FlowerSupernodeRegistrationBO registrationBO() {
        FlowerSupernodeRegistrationBO bo = new FlowerSupernodeRegistrationBO();
        bo.attemptAO = mock(ExecutionAttemptAO.class);
        bo.projectAO = mock(LearningProjectAO.class);
        bo.websocketSender = mock(WebsocketSender.class);
        bo.executionRunBO = mock(ExecutionRunBO.class);

        ExecutionRunEntity run = new ExecutionRunEntity();
        run.setId(RUN_ID);
        run.setParticipantId(CLINIC);
        LearningRequestEntity request = new LearningRequestEntity();
        request.setGlobalExperimentUniqueId(EXPERIMENT);
        LearningProjectEntity project = new LearningProjectEntity();
        project.setRequest(request);
        when(bo.projectAO.findByRunId(RUN_ID)).thenReturn(project);
        WorkflowNodeEntity node = new WorkflowNodeEntity();
        node.setNodeId(NODE);
        ExecutionRunStepEntity step = new ExecutionRunStepEntity();
        step.setRun(run);
        step.setWorkflowNode(node);
        ExecutionAttemptEntity attempt = new ExecutionAttemptEntity();
        attempt.setId(ATTEMPT_ID);
        attempt.setStep(step);
        when(bo.attemptAO.findById(ATTEMPT_ID)).thenReturn(attempt);
        return bo;
    }

    @Test
    void forwardsTheAppsKeyForItsTrainingAndStepToTheGlobalServer() {
        FlowerSupernodeRegistrationBO bo = registrationBO();

        bo.forwardKeyTransactional(ATTEMPT_ID, new FlowerSupernodeKeyDTO("request-1", KEY, null));

        verify(bo.websocketSender).sendFlowerSupernodeKey(
                new FlowerSupernodeKeyRequestDTO("request-1", EXPERIMENT, CLINIC, NODE, KEY));
    }

    @Test
    @SuppressWarnings("unchecked")
    void handsTheAnswerToTheAttemptThatAsked() {
        FlowerSupernodeRegistrationBO bo = registrationBO();
        bo.forwardKeyTransactional(ATTEMPT_ID, new FlowerSupernodeKeyDTO("request-1", KEY, null));

        bo.forwardAnswer(new FlowerSupernodeRegisteredDTO("request-1", true, null));

        ArgumentCaptor<AppMessageWrapperDTO<FlowerSupernodeRegisteredDTO>> sent = ArgumentCaptor.forClass(AppMessageWrapperDTO.class);
        verify(bo.executionRunBO).sendToApp(eq(ATTEMPT_ID), sent.capture());
        assertEquals(AppMessageTypeEnum.FLOWER_SUPERNODE_REGISTERED, sent.getValue().getType());
        assertTrue(sent.getValue().getMessage().isSuccess());
    }

    @Test
    void ignoresAnswersNobodyHereIsWaitingFor() {
        FlowerSupernodeRegistrationBO bo = registrationBO();

        bo.forwardAnswer(new FlowerSupernodeRegisteredDTO("unknown", true, null));

        verify(bo.executionRunBO, never()).sendToApp(anyLong(), any());
    }

    @Test
    void ignoresKeysOfUnknownAttemptsOrWithoutAKey() {
        FlowerSupernodeRegistrationBO bo = registrationBO();

        bo.forwardKeyTransactional(99L, new FlowerSupernodeKeyDTO("request-1", KEY, null));
        bo.forwardKeyTransactional(ATTEMPT_ID, new FlowerSupernodeKeyDTO("request-1", null, null));

        verify(bo.websocketSender, never()).sendFlowerSupernodeKey(any());
    }
}
