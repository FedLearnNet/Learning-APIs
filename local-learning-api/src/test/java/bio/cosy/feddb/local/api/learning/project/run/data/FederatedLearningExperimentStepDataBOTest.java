package bio.cosy.feddb.local.api.learning.project.run.data;

import bio.cosy.feddb.local.api.learning.project.run.step.FederatedLearningExperimentStepAO;
import bio.cosy.feddb.local.api.learning.project.run.step.FederatedLearningExperimentStepEntity;
import bio.cosy.feddb.local.services.orch.WorkflowOrchestratorBO;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class FederatedLearningExperimentStepDataBOTest {
    @Test
    void collectsByExecutionOrderAndPersistsUnderDatabaseIds() {
        var service = spy(new FederatedLearningExperimentStepDataBO());
        service.stepAO = mock(FederatedLearningExperimentStepAO.class);
        service.workflowOrchestratorBO = mock(WorkflowOrchestratorBO.class);
        var step = mock(FederatedLearningExperimentStepEntity.class, RETURNS_DEEP_STUBS);
        when(service.stepAO.findById(20L)).thenReturn(step);
        when(step.getWorkflowNode().getId()).thenReturn(891L);
        when(step.getWorkflowNode().getExecutionOrder()).thenReturn(0);
        when(step.getExperiment().getId()).thenReturn(20L);
        List<File> files = List.of(new File("predictions.csv"));
        when(service.workflowOrchestratorBO.getFiles(20L, 0L)).thenReturn(files);
        doReturn(List.of()).when(service).createForStep(files, 891L, 20L, 20L);

        service.saveResults(20L);

        verify(service.workflowOrchestratorBO).getFiles(20L, 0L);
        verify(service.workflowOrchestratorBO, never()).getFiles(20L, 20L);
        verify(service).createForStep(files, 891L, 20L, 20L);
    }

    @Test
    void missingExecutionOrderFailsBeforeDownloading() {
        var service = new FederatedLearningExperimentStepDataBO();
        service.workflowOrchestratorBO = mock(WorkflowOrchestratorBO.class);
        var context = new StepResultContext(20L, 891L, null, 20L, 1L, "clinic", "run", false, false, null);

        assertThrows(IllegalStateException.class, () -> service.saveResults(context));
        verifyNoInteractions(service.workflowOrchestratorBO);
    }
}
