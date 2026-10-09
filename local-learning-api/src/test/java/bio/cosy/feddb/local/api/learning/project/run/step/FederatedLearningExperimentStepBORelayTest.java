package bio.cosy.feddb.local.api.learning.project.run.step;

import bio.cosy.feddb.core.api.socket.FederatedLearningRelayInfoDTO;
import bio.cosy.feddb.core.services.controller.ControllerStopLearningRequestDTO;
import bio.cosy.feddb.local.services.controller.LocalControllerLearningService;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FederatedLearningExperimentStepBORelayTest {

    private FederatedLearningExperimentStepBO stepBO() {
        FederatedLearningExperimentStepBO bo = new FederatedLearningExperimentStepBO();
        bo.ao = mock(FederatedLearningExperimentStepAO.class);
        bo.controllerService = mock(LocalControllerLearningService.class);
        return bo;
    }

    private FederatedLearningExperimentStepEntity step(long id, String csr) {
        FederatedLearningRelayInfoDTO relay = new FederatedLearningRelayInfoDTO();
        relay.setId("client-" + id);
        relay.setChannel("channel-" + id);
        FederatedLearningExperimentStepEntity step = new FederatedLearningExperimentStepEntity();
        step.setId(id);
        step.setRelayInfo(relay);
        step.setRelayCsr(csr);
        return step;
    }

    @Test
    void stopsTheControllerRunOfTheStep() {
        FederatedLearningExperimentStepBO bo = stepBO();
        when(bo.ao.findById(1L)).thenReturn(step(1L, "csr"));
        when(bo.controllerService.stopLearning(any())).thenReturn(Response.ok().build());

        bo.stopControllerRun(1L);

        // the app key of the controller run is the relay client id
        verify(bo.controllerService).stopLearning(new ControllerStopLearningRequestDTO("channel-1", "client-1"));
        verify(bo.ao).setRelayStoppedTransactional(1L);
    }

    @Test
    void runUnknownToTheControllerCountsAsStopped() {
        FederatedLearningExperimentStepBO bo = stepBO();
        when(bo.ao.findById(1L)).thenReturn(step(1L, "csr"));
        when(bo.controllerService.stopLearning(any())).thenThrow(new WebApplicationException(404));

        bo.stopControllerRun(1L);

        verify(bo.ao).setRelayStoppedTransactional(1L);
    }

    @Test
    void skipsStepsWithoutOpenControllerRun() {
        FederatedLearningExperimentStepBO bo = stepBO();
        FederatedLearningExperimentStepEntity neverStarted = step(1L, null);
        FederatedLearningExperimentStepEntity stopped = step(2L, "csr");
        stopped.setRelayStopped(true);
        FederatedLearningExperimentStepEntity noRelay = new FederatedLearningExperimentStepEntity();
        noRelay.setId(3L);
        when(bo.ao.findById(1L)).thenReturn(neverStarted);
        when(bo.ao.findById(2L)).thenReturn(stopped);
        when(bo.ao.findById(3L)).thenReturn(noRelay);

        bo.stopControllerRun(1L);
        bo.stopControllerRun(2L);
        bo.stopControllerRun(3L);
        bo.stopControllerRun(4L);

        verify(bo.controllerService, never()).stopLearning(any());
        verify(bo.ao, never()).setRelayStoppedTransactional(anyLong());
    }

    @Test
    void stopsEveryOpenRunOfTheExperimentAndSurvivesControllerFailures() {
        FederatedLearningExperimentStepBO bo = stepBO();
        when(bo.ao.findWithOpenControllerRun(5L)).thenReturn(List.of(step(1L, "csr"), step(2L, "csr"), step(3L, "csr")));
        when(bo.controllerService.stopLearning(new ControllerStopLearningRequestDTO("channel-1", "client-1")))
                .thenThrow(new ProcessingException("controller unreachable"));
        when(bo.controllerService.stopLearning(new ControllerStopLearningRequestDTO("channel-2", "client-2")))
                .thenReturn(Response.serverError().build());
        when(bo.controllerService.stopLearning(new ControllerStopLearningRequestDTO("channel-3", "client-3")))
                .thenReturn(Response.ok().build());

        bo.stopControllerRuns(5L);

        // failed stops stay open so they are tried again, the successful one is marked
        verify(bo.ao, never()).setRelayStoppedTransactional(1L);
        verify(bo.ao, never()).setRelayStoppedTransactional(2L);
        verify(bo.ao).setRelayStoppedTransactional(3L);
    }
}
