package bio.cosy.feddb.local.api.learning.project.run;

import bio.cosy.feddb.core.api.run.AppRunTypeEnum;
import bio.cosy.feddb.core.api.run.AppMessageTypeEnum;
import bio.cosy.feddb.core.api.run.AppMessageWrapperDTO;
import bio.cosy.feddb.core.api.run.FinishRunDTO;
import bio.cosy.feddb.core.api.run.RunStatusTypes;
import bio.cosy.feddb.core.api.run.StartRunDTO;
import bio.cosy.feddb.core.api.run.UpdateRunDTO;
import bio.cosy.feddb.local.api.learning.project.run.step.FederatedLearningExperimentStepBO;
import bio.cosy.feddb.local.api.learning.project.run.step.FederatedLearningExperimentStepDTO;
import bio.cosy.feddb.local.api.learning.project.run.data.FederatedLearningExperimentStepDataBO;
import io.quarkus.websockets.next.WebSocketConnection;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FederatedLearningExperimentWebsocketServiceTest {

    private FederatedLearningExperimentWebsocketService finishService() {
        FederatedLearningExperimentWebsocketService service = new FederatedLearningExperimentWebsocketService();
        service.stepBO = mock(FederatedLearningExperimentStepBO.class);
        service.stepResultBO = mock(FederatedLearningExperimentStepDataBO.class);
        service.connection = mock(WebSocketConnection.class);
        when(service.connection.pathParam("runId")).thenReturn("19");
        when(service.connection.pathParam("type")).thenReturn("app");
        FederatedLearningExperimentStepDTO step = new FederatedLearningExperimentStepDTO();
        step.setId(19L);
        step.setStepStatus(RunStatusTypes.RUNNING);
        when(service.stepBO.getById(19L)).thenReturn(step);
        return service;
    }

    @Test
    void failedFederatedCompletionPersistsErrorWithoutEarlierUpdate() {
        var service = finishService();
        // Exercise the wire-message conversion too; the body cannot redirect the authenticated run.
        service.consume(new AppMessageWrapperDTO<>(AppMessageTypeEnum.FINISH_FEDERATED_RUN,
                Map.of("id", 99, "status", "ERROR", "error", "Relay connection timed out"),
                AppRunTypeEnum.FEDERATED_RUN));

        var step = service.stepBO.getById(19L);
        assertEquals(RunStatusTypes.ERROR, step.getStepStatus());
        assertEquals("Relay connection timed out", step.getLastError());
        verify(service.stepBO).updateStatus(step);
        verify(service.stepBO, never()).getById(99L);
        verify(service.stepResultBO, never()).saveResults(19L);
    }

    @Test
    void errorMessagePreventsSuccessEvenWithFinishedStatus() {
        var service = finishService();
        FinishRunDTO finish = new FinishRunDTO();
        finish.setStatus(RunStatusTypes.FINISHED);
        finish.setError("Training failed");
        service.finishRun(19L, finish, AppRunTypeEnum.FEDERATED_RUN);

        var step = service.stepBO.getById(19L);
        assertEquals(RunStatusTypes.ERROR, step.getStepStatus());
        assertEquals("Training failed", step.getLastError());
        verify(service.stepBO).updateStatus(step);
        verify(service.stepResultBO, never()).saveResults(19L);
    }

    @Test
    void errorStatusWithoutMessageGetsPersistableReason() {
        var service = finishService();
        FinishRunDTO finish = new FinishRunDTO();
        finish.setStatus(RunStatusTypes.ERROR);
        service.finishRun(19L, finish, AppRunTypeEnum.FEDERATED_RUN);

        var step = service.stepBO.getById(19L);
        assertEquals(RunStatusTypes.ERROR, step.getStepStatus());
        assertEquals("Run finished with error", step.getLastError());
        verify(service.stepBO).updateStatus(step);
    }

    @Test
    void completionPreservesPreviouslyRecordedError() {
        var service = finishService();
        var step = service.stepBO.getById(19L);
        step.setStepStatus(RunStatusTypes.ERROR);
        step.setLastError("Original training failure");
        FinishRunDTO finish = new FinishRunDTO();
        finish.setStatus(RunStatusTypes.FINISHED);
        service.finishRun(19L, finish, AppRunTypeEnum.FEDERATED_RUN);

        assertEquals(RunStatusTypes.ERROR, step.getStepStatus());
        assertEquals("Original training failure", step.getLastError());
        verify(service.stepResultBO, never()).saveResults(19L);
    }

    @Test
    void legacyCompletionStillSavesResultsAndFinishes() {
        var service = finishService();
        service.finishRun(19L, new FinishRunDTO(), AppRunTypeEnum.EXPERIMENT_RUN);

        var step = service.stepBO.getById(19L);
        assertEquals(RunStatusTypes.FINISHED, step.getStepStatus());
        assertNull(step.getLastError());
        verify(service.stepResultBO).saveResults(19L);
        verify(service.stepBO).updateStatus(step);
    }

    @Test
    void resultSavingFailureCannotBecomeSuccessfulCompletion() {
        var service = finishService();
        doThrow(new IllegalStateException("Output volume unavailable"))
                .when(service.stepResultBO).saveResults(19L);
        service.finishRun(19L, new FinishRunDTO(), AppRunTypeEnum.FEDERATED_RUN);

        var step = service.stepBO.getById(19L);
        assertEquals(RunStatusTypes.ERROR, step.getStepStatus());
        assertEquals("Failed to save results: Output volume unavailable", step.getLastError());
        verify(service.stepBO).updateStatus(step);
    }

    @Test
    void messageBodyCannotRedirectUpdatesToAnotherRun() {
        FederatedLearningExperimentWebsocketService service =
                new FederatedLearningExperimentWebsocketService();
        service.stepBO = mock(FederatedLearningExperimentStepBO.class);
        FederatedLearningExperimentStepDTO authenticatedStep =
                new FederatedLearningExperimentStepDTO();
        when(service.stepBO.getById(3L)).thenReturn(authenticatedStep);

        StartRunDTO start = new StartRunDTO();
        start.setId(2L);
        service.startRun(3L, start, AppRunTypeEnum.MODEL_RUN);

        UpdateRunDTO update = new UpdateRunDTO();
        update.setRunId(2L);
        update.setStatus(RunStatusTypes.RUNNING);
        service.updateRun(3L, update, AppRunTypeEnum.MODEL_RUN);

        verify(service.stepBO, never()).getById(2L);
        verify(service.stepBO, times(2)).updateStatus(authenticatedStep);
    }
}
