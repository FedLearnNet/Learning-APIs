package de.unihamburg.daibetes.api.project.experiment.federated.step;

import bio.cosy.feddb.core.services.controller.RelayStopRequestDTO;
import de.unihamburg.daibetes.api.project.experiment.federated.ProjectFederatedExperimentEntity;
import de.unihamburg.daibetes.api.project.experiment.federated.participants.ProjectFederatedExperimentParticipantAO;
import de.unihamburg.daibetes.api.project.experiment.federated.participants.ProjectFederatedExperimentParticipantEntity;
import de.unihamburg.daibetes.services.GlobalRelayService;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProjectFederatedExperimentStepBORelayTest {

    private static final String EXPERIMENT = "experiment-1";
    private static final long EXPERIMENT_ID = 7L;
    private static final String NODE = "node-1";

    private ProjectFederatedExperimentStepBO stepBO() {
        ProjectFederatedExperimentStepBO bo = new ProjectFederatedExperimentStepBO();
        bo.ao = mock(ProjectFederatedExperimentStepAO.class);
        bo.participantAO = mock(ProjectFederatedExperimentParticipantAO.class);
        bo.globalRelayService = mock(GlobalRelayService.class);

        ProjectFederatedExperimentEntity experiment = new ProjectFederatedExperimentEntity();
        experiment.setId(EXPERIMENT_ID);
        for (String clinic : List.of("clinic-a", "clinic-b")) {
            ProjectFederatedExperimentParticipantEntity participant = new ProjectFederatedExperimentParticipantEntity();
            participant.setExperiment(experiment);
            when(bo.participantAO.findByExperimentIdAndClinicId(EXPERIMENT, clinic)).thenReturn(Optional.of(participant));
        }
        when(bo.participantAO.findByExperimentIdAndClinicId(EXPERIMENT, "clinic-unknown")).thenReturn(Optional.empty());
        return bo;
    }

    private ProjectFederatedExperimentStepEntity step(long id, String channel) {
        ProjectFederatedExperimentStepEntity step = new ProjectFederatedExperimentStepEntity();
        step.setId(id);
        step.setChannel(channel);
        step.setRelayClientIds(Map.of("clinic-a", "client-a", "clinic-b", "client-b"));
        return step;
    }

    @Test
    void channelIsOnlyReturnedForTheClientAssignedToTheClinic() {
        ProjectFederatedExperimentStepBO bo = stepBO();
        when(bo.ao.findByWorkflowNodeId(EXPERIMENT_ID, NODE)).thenReturn(Optional.of(step(1L, "channel-1")));

        assertEquals(Optional.of("channel-1"), bo.findRelayChannelForClient(EXPERIMENT, NODE, "clinic-a", "client-a"));
        assertEquals(Optional.of("channel-1"), bo.findRelayChannelForClient(EXPERIMENT, NODE, "clinic-b", "client-b"));
        // relay client of another clinic
        assertTrue(bo.findRelayChannelForClient(EXPERIMENT, NODE, "clinic-a", "client-b").isEmpty());
        assertTrue(bo.findRelayChannelForClient(EXPERIMENT, NODE, "clinic-unknown", "client-a").isEmpty());
        assertTrue(bo.findRelayChannelForClient(EXPERIMENT, "node-unknown", "clinic-a", "client-a").isEmpty());
        assertTrue(bo.findRelayChannelForClient(EXPERIMENT, NODE, "clinic-a", null).isEmpty());
    }

    // Each step has its own relay run: a client id of one step must not be signed for another step
    @Test
    void channelIsLookedUpPerStep() {
        ProjectFederatedExperimentStepBO bo = stepBO();
        ProjectFederatedExperimentStepEntity otherStep = step(2L, "channel-2");
        otherStep.setRelayClientIds(Map.of("clinic-a", "client-a-step-2"));
        when(bo.ao.findByWorkflowNodeId(EXPERIMENT_ID, NODE)).thenReturn(Optional.of(step(1L, "channel-1")));
        when(bo.ao.findByWorkflowNodeId(EXPERIMENT_ID, "node-2")).thenReturn(Optional.of(otherStep));

        assertEquals(Optional.of("channel-2"), bo.findRelayChannelForClient(EXPERIMENT, "node-2", "clinic-a", "client-a-step-2"));
        assertTrue(bo.findRelayChannelForClient(EXPERIMENT, NODE, "clinic-a", "client-a-step-2").isEmpty());
        assertTrue(bo.findRelayChannelForClient(EXPERIMENT, "node-2", "clinic-a", "client-a").isEmpty());
    }

    @Test
    void noChannelForStepWithoutRelayRunOrStoppedRelayRun() {
        ProjectFederatedExperimentStepBO bo = stepBO();
        when(bo.ao.findByWorkflowNodeId(EXPERIMENT_ID, NODE)).thenReturn(Optional.of(step(1L, null)));
        assertTrue(bo.findRelayChannelForClient(EXPERIMENT, NODE, "clinic-a", "client-a").isEmpty());

        ProjectFederatedExperimentStepEntity stopped = step(1L, "channel-1");
        stopped.setRelayStopped(true);
        when(bo.ao.findByWorkflowNodeId(EXPERIMENT_ID, NODE)).thenReturn(Optional.of(stopped));
        assertTrue(bo.findRelayChannelForClient(EXPERIMENT, NODE, "clinic-a", "client-a").isEmpty());
    }

    @Test
    void stopRelayRunStopsTheChannelOnce() {
        ProjectFederatedExperimentStepBO bo = stepBO();
        when(bo.ao.findById(1L)).thenReturn(step(1L, "channel-1"));
        when(bo.globalRelayService.stopFL(new RelayStopRequestDTO("channel-1"))).thenReturn(Response.ok().build());

        bo.stopRelayRun(1L);

        verify(bo.globalRelayService).stopFL(new RelayStopRequestDTO("channel-1"));
        verify(bo.ao).setRelayStoppedTransactional(1L);
    }

    @Test
    void stopRelayRunSkipsStepsWithoutOpenRelayRun() {
        ProjectFederatedExperimentStepBO bo = stepBO();
        ProjectFederatedExperimentStepEntity stopped = step(2L, "channel-2");
        stopped.setRelayStopped(true);
        when(bo.ao.findById(1L)).thenReturn(step(1L, null));
        when(bo.ao.findById(2L)).thenReturn(stopped);

        bo.stopRelayRun(1L);
        bo.stopRelayRun(2L);
        bo.stopRelayRun(3L);

        verify(bo.globalRelayService, never()).stopFL(any());
    }

    @Test
    void stopRelayRunsStopsEveryOpenRelayRunAndSurvivesRelayFailures() {
        ProjectFederatedExperimentStepBO bo = stepBO();
        when(bo.ao.findWithOpenRelayRun(EXPERIMENT_ID)).thenReturn(List.of(step(1L, "channel-1"), step(2L, "channel-2")));
        when(bo.globalRelayService.stopFL(new RelayStopRequestDTO("channel-1"))).thenThrow(new ProcessingException("relay unreachable"));
        when(bo.globalRelayService.stopFL(new RelayStopRequestDTO("channel-2"))).thenReturn(Response.ok().build());

        bo.stopRelayRuns(EXPERIMENT_ID);

        // the unreachable one stays open, the other one is stopped
        verify(bo.ao, never()).setRelayStoppedTransactional(1L);
        verify(bo.ao).setRelayStoppedTransactional(2L);
        verify(bo.ao, never()).updateStatusTransactional(anyLong(), any());
    }
}
