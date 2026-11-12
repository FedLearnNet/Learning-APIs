package de.unihamburg.daibetes.api.project.experiment.federated.aggregator;

import bio.cosy.feddb.core.api.app.FederatedAppDetailDTO;
import bio.cosy.feddb.core.api.project.ProjectStatus;
import bio.cosy.feddb.core.api.run.RunStatusTypes;
import bio.cosy.feddb.core.api.workflow.node.WorkflowNodeDetailDTO;
import bio.cosy.feddb.core.security.Scope;
import bio.cosy.feddb.core.security.ToolApiKeyService;
import de.unihamburg.daibetes.api.project.experiment.federated.ProjectFederatedExperimentBO;
import de.unihamburg.daibetes.api.project.experiment.federated.ProjectFederatedExperimentEntity;
import de.unihamburg.daibetes.api.project.experiment.federated.step.ProjectFederatedExperimentStepBO;
import de.unihamburg.daibetes.api.project.experiment.federated.step.ProjectFederatedExperimentStepEntity;
import de.unihamburg.daibetes.api.workflow.WorkflowBO;
import de.unihamburg.daibetes.services.WorkflowOrchestratorBO;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ProjectFederatedExperimentFlowerAggregatorBOTest {

    private static final Long STEP_ID = 42L;
    private static final String API_KEY = "api-key-1";
    private static final String CONTAINER_ID = "flower-container-1";

    private ProjectFederatedExperimentFlowerAggregatorBO flowerAggregatorBO() {
        ProjectFederatedExperimentFlowerAggregatorBO bo = new ProjectFederatedExperimentFlowerAggregatorBO();
        bo.workflowOrchestratorBO = mock(WorkflowOrchestratorBO.class);
        bo.toolApiKeyService = mock(ToolApiKeyService.class);
        bo.stepBO = mock(ProjectFederatedExperimentStepBO.class);
        bo.workflowBO = mock(WorkflowBO.class);
        bo.experimentBO = mock(ProjectFederatedExperimentBO.class);
        when(bo.toolApiKeyService.issue(Scope.FEDERATED_AGGREGATOR_RUN, STEP_ID)).thenReturn(API_KEY);
        return bo;
    }

    private ProjectFederatedExperimentEntity experiment() {
        ProjectFederatedExperimentStepEntity step = new ProjectFederatedExperimentStepEntity();
        step.setId(STEP_ID);
        ProjectFederatedExperimentEntity experiment = new ProjectFederatedExperimentEntity();
        experiment.setId(1L);
        experiment.setCurrentWorkflowNode(step);
        return experiment;
    }

    private WorkflowNodeDetailDTO node() {
        WorkflowNodeDetailDTO node = new WorkflowNodeDetailDTO();
        node.setId(STEP_ID);
        node.setWorkflowId(1L);
        node.setExecutionOrder(0);
        FederatedAppDetailDTO app = new FederatedAppDetailDTO();
        app.setImageName("some-flower-image:latest");
        node.setAppDetail(app);
        return node;
    }

    @Test
    void startExecutesWorkflowAndPersistsContainerId() {
        ProjectFederatedExperimentFlowerAggregatorBO bo = flowerAggregatorBO();
        when(bo.workflowOrchestratorBO.executeWorkflow(any(), eq(ProjectFederatedExperimentFlowerAggregatorBO.WS_BASE_URL)))
                .thenReturn(CONTAINER_ID);

        bo.start(experiment(), node());

        verify(bo.workflowOrchestratorBO).executeWorkflow(any(), eq(ProjectFederatedExperimentFlowerAggregatorBO.WS_BASE_URL));
        verify(bo.stepBO).setFlowerAggregatorContainer(STEP_ID, CONTAINER_ID);
        verify(bo.toolApiKeyService, never()).revoke(any(), any());
    }

    @Test
    void executeWorkflowFailureRevokesApiKeyAndRethrows() {
        ProjectFederatedExperimentFlowerAggregatorBO bo = flowerAggregatorBO();
        when(bo.workflowOrchestratorBO.executeWorkflow(any(), eq(ProjectFederatedExperimentFlowerAggregatorBO.WS_BASE_URL)))
                .thenThrow(new IllegalStateException("orch-api rejected the start"));

        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                () -> bo.start(experiment(), node()));

        verify(bo.toolApiKeyService).revoke(Scope.FEDERATED_AGGREGATOR_RUN, STEP_ID);
        verify(bo.stepBO, never()).setFlowerAggregatorContainer(any(), any());
    }

    @Test
    void stopNoOpsWhenStepHasNoContainer() {
        ProjectFederatedExperimentFlowerAggregatorBO bo = flowerAggregatorBO();
        ProjectFederatedExperimentStepEntity step = new ProjectFederatedExperimentStepEntity();
        step.setId(STEP_ID);
        when(bo.stepBO.findEntityByIdOptional(STEP_ID)).thenReturn(Optional.of(step));

        bo.stop(STEP_ID);

        verifyNoInteractions(bo.workflowOrchestratorBO);
        verify(bo.toolApiKeyService, never()).revoke(any(), any());
    }

    @Test
    void stopCleansUpContainerAndRevokesApiKey() {
        ProjectFederatedExperimentFlowerAggregatorBO bo = flowerAggregatorBO();
        ProjectFederatedExperimentStepEntity step = new ProjectFederatedExperimentStepEntity();
        step.setId(STEP_ID);
        step.setContainerId(CONTAINER_ID);
        when(bo.stepBO.findEntityByIdOptional(STEP_ID)).thenReturn(Optional.of(step));

        bo.stop(STEP_ID);

        verify(bo.workflowOrchestratorBO).cleanup(CONTAINER_ID, true);
        verify(bo.toolApiKeyService).revoke(Scope.FEDERATED_AGGREGATOR_RUN, STEP_ID);
    }

    @Test
    void onStatusUpdateFinishedNotifiesExperimentBO() {
        ProjectFederatedExperimentFlowerAggregatorBO bo = flowerAggregatorBO();

        bo.onStatusUpdate(STEP_ID, RunStatusTypes.FINISHED, null);

        verify(bo.experimentBO).onPlatformAggregatorFinished(STEP_ID);
    }

    @Test
    void onStatusUpdateErrorStopsLearning() {
        ProjectFederatedExperimentFlowerAggregatorBO bo = flowerAggregatorBO();
        ProjectFederatedExperimentEntity experiment = experiment();
        ProjectFederatedExperimentStepEntity step = new ProjectFederatedExperimentStepEntity();
        step.setId(STEP_ID);
        step.setExperiment(experiment);
        when(bo.stepBO.findEntityByIdOptional(STEP_ID)).thenReturn(Optional.of(step));

        bo.onStatusUpdate(STEP_ID, RunStatusTypes.ERROR, "SuperLink crashed");

        verify(bo.experimentBO).stopLearning(experiment, ProjectStatus.ERROR);
        verify(bo.experimentBO, never()).onPlatformAggregatorFinished(any());
    }

    @Test
    void onStatusUpdateRunningIsANoOp() {
        ProjectFederatedExperimentFlowerAggregatorBO bo = flowerAggregatorBO();

        bo.onStatusUpdate(STEP_ID, RunStatusTypes.RUNNING, null);

        verifyNoInteractions(bo.experimentBO);
    }

    @Test
    void onConnectionClosedTreatsUnexpectedDisconnectAsError() {
        ProjectFederatedExperimentFlowerAggregatorBO bo = flowerAggregatorBO();
        ProjectFederatedExperimentEntity experiment = experiment();
        experiment.setExperimentStatus(ProjectStatus.RUNNING);
        ProjectFederatedExperimentStepEntity step = experiment.getCurrentWorkflowNode();
        step.setExperiment(experiment);
        step.setContainerId(CONTAINER_ID);
        step.setPlatformAggregatorFinished(false);
        when(bo.stepBO.findEntityByIdOptional(STEP_ID)).thenReturn(Optional.of(step));

        bo.onConnectionClosed(STEP_ID);

        verify(bo.experimentBO).stopLearning(experiment, ProjectStatus.ERROR);
    }

    @Test
    void onConnectionClosedSkipsStepsThePlatformStoppedItself() {
        ProjectFederatedExperimentFlowerAggregatorBO bo = flowerAggregatorBO();
        ProjectFederatedExperimentStepEntity step = new ProjectFederatedExperimentStepEntity();
        step.setId(STEP_ID);
        step.setContainerId(CONTAINER_ID);
        when(bo.stepBO.findEntityByIdOptional(STEP_ID)).thenReturn(Optional.of(step));

        bo.stop(STEP_ID); // marks STEP_ID as self-stopped
        bo.onConnectionClosed(STEP_ID);

        verifyNoInteractions(bo.experimentBO);
    }
}
