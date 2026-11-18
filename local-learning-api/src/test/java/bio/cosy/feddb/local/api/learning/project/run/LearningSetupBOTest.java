package bio.cosy.feddb.local.api.learning.project.run;

import bio.cosy.feddb.core.api.app.CommunicationEngine;
import bio.cosy.feddb.core.execution.ExecutionMode;
import bio.cosy.feddb.core.execution.run.dto.FederatedRunConfigDTO;
import bio.cosy.feddb.core.execution.run.dto.StartRunDTO;
import bio.cosy.feddb.local.api.learning.project.LearningProjectAO;
import bio.cosy.feddb.local.api.learning.project.LearningProjectEntity;
import bio.cosy.feddb.local.api.workflow.WorkflowBO;
import bio.cosy.feddb.local.api.workflow.WorkflowEntity;
import bio.cosy.feddb.local.api.workflow.node.WorkflowNodeBO;
import bio.cosy.feddb.local.api.workflow.node.WorkflowNodeEntity;
import bio.cosy.feddb.local.execution.attempt.ExecutionAttemptEntity;
import bio.cosy.feddb.local.execution.federation.ExecutionFederationEntity;
import bio.cosy.feddb.local.execution.run.ExecutionRunEntity;
import bio.cosy.feddb.local.execution.step.ExecutionRunStepEntity;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Covers the {@code customizeStartup} branch added for ticket #13: a clinic's own step dispatches
 * to {@code FederatedRunEnricher.enrichWithFlower} instead of the FL-Net relay enrichment whenever
 * the step's federation config carries a {@code superlinkAddress} - the discriminator
 * {@link FederatedRunConfigDTO#getSuperlinkAddress()} exists for, since local's own
 * {@code WorkflowNodeEntity} does not (and does not need to) carry a communicationEngine field.
 */
class LearningSetupBOTest {

    private LearningSetupBO setupBO() {
        LearningSetupBO bo = new LearningSetupBO();
        bo.workflowBO = mock(WorkflowBO.class);
        bo.workflowNodeBO = mock(WorkflowNodeBO.class);
        bo.projectAO = mock(LearningProjectAO.class);
        return bo;
    }

    // A step first in its run's execution order, reading no external database - the simplest case
    // that reaches the federation dispatch at the end of customizeStartup.
    private ExecutionRunStepEntity stepWithNoDatabase() {
        WorkflowEntity workflow = new WorkflowEntity();
        WorkflowNodeEntity node = new WorkflowNodeEntity();
        node.setWorkflow(workflow);

        ExecutionRunEntity run = new ExecutionRunEntity();
        run.setMode(ExecutionMode.FEDERATED);

        ExecutionRunStepEntity step = new ExecutionRunStepEntity();
        step.setWorkflowNode(node);
        step.setRun(run);
        run.setSteps(List.of(step));
        return step;
    }

    private void stubNoInputsNoTreSource(LearningSetupBO bo, ExecutionRunStepEntity step) {
        when(bo.workflowNodeBO.toDetailDto(step.getWorkflowNode()))
                .thenReturn(new bio.cosy.feddb.core.api.workflow.node.WorkflowNodeDetailDTO());
        when(bo.projectAO.findByRunId(step.getRun().getId())).thenReturn(new LearningProjectEntity());
    }

    @Test
    void aFlowerFederationConfigDispatchesToEnrichWithFlowerAsAClient() {
        LearningSetupBO bo = setupBO();
        ExecutionRunStepEntity step = stepWithNoDatabase();
        stubNoInputsNoTreSource(bo, step);
        FederatedRunConfigDTO config = new FederatedRunConfigDTO();
        config.setSuperlinkAddress("platform.example:8000");
        ExecutionFederationEntity federation = new ExecutionFederationEntity();
        federation.setConfig(config);
        federation.setStartAggregator(true); // must be ignored - a clinic is never the Flower aggregator
        step.setFederation(federation);
        ExecutionAttemptEntity attempt = new ExecutionAttemptEntity();
        attempt.setStep(step);
        StartRunDTO start = new StartRunDTO();

        bo.customizeStartup(attempt, start);

        assertEquals(CommunicationEngine.FLOWER, start.getCommunicationEngine());
        assertEquals(1, start.getParticipants().size());
        assertEquals("CLIENT", start.getParticipants().get(0).getRole());
        assertEquals(config, start.getConfig());
    }

    @Test
    void anFlNetFederationConfigStillDispatchesToTheRelayEnrichment() {
        LearningSetupBO bo = setupBO();
        ExecutionRunStepEntity step = stepWithNoDatabase();
        stubNoInputsNoTreSource(bo, step);
        FederatedRunConfigDTO config = new FederatedRunConfigDTO();
        config.setChannel("channel-1");
        config.setClientId("clinic-1");
        ExecutionFederationEntity federation = new ExecutionFederationEntity();
        federation.setConfig(config);
        federation.setStartAggregator(false);
        step.setFederation(federation);
        ExecutionAttemptEntity attempt = new ExecutionAttemptEntity();
        attempt.setStep(step);
        StartRunDTO start = new StartRunDTO();

        bo.customizeStartup(attempt, start);

        assertEquals(CommunicationEngine.FL_NET, start.getCommunicationEngine());
        assertEquals(1, start.getParticipants().size());
        assertEquals("CLIENT", start.getParticipants().get(0).getRole());
        assertEquals("clinic-1", start.getParticipants().get(0).getParticipantId());
    }

    @Test
    void noFederationAtAllLeavesCommunicationEngineAtItsDefault() {
        LearningSetupBO bo = setupBO();
        ExecutionRunStepEntity step = stepWithNoDatabase();
        stubNoInputsNoTreSource(bo, step);
        ExecutionAttemptEntity attempt = new ExecutionAttemptEntity();
        attempt.setStep(step);
        StartRunDTO start = new StartRunDTO();

        bo.customizeStartup(attempt, start);

        assertEquals(CommunicationEngine.FL_NET, start.getCommunicationEngine());
        assertNull(start.getParticipants());
    }
}
