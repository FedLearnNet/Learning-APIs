package bio.cosy.feddb.local.execution.coordinator;

import bio.cosy.feddb.core.api.workflow.WorkflowDTO;
import bio.cosy.feddb.core.api.app.FederatedAppDetailDTO;
import bio.cosy.feddb.core.api.workflow.node.WorkflowNodeDetailDTO;
import bio.cosy.feddb.core.execution.ExecutionPurpose;
import bio.cosy.feddb.core.execution.coordinator.BaseAttemptLauncher;
import bio.cosy.feddb.core.execution.coordinator.ExecutionCallback;
import bio.cosy.feddb.core.execution.event.AttemptFinishedEvent;
import bio.cosy.feddb.core.execution.event.AttemptLaunchEvent;
import bio.cosy.feddb.core.execution.event.ExecutionChannels;
import bio.cosy.feddb.core.execution.event.RunFinishedEvent;
import bio.cosy.feddb.core.execution.orchestrator.WorkflowOrchestrator;
import bio.cosy.feddb.core.security.Scope;
import bio.cosy.feddb.core.services.orch.dto.StartWorkflowNodeDTO;
import bio.cosy.feddb.local.api.cohort.patient.tools.PatientToolBO;
import bio.cosy.feddb.local.api.learning.project.run.FederatedLearningRunBO;
import bio.cosy.feddb.local.api.workflow.WorkflowBO;
import bio.cosy.feddb.local.api.workflow.node.WorkflowNodeBO;
import bio.cosy.feddb.local.api.workflow.node.WorkflowNodeEntity;
import bio.cosy.feddb.local.execution.attempt.ExecutionAttemptAO;
import bio.cosy.feddb.local.execution.attempt.ExecutionAttemptEntity;
import bio.cosy.feddb.local.execution.step.ExecutionRunStepEntity;
import bio.cosy.feddb.local.services.GlobalAPIStoreService;
import io.quarkus.logging.Log;
import io.quarkus.virtual.threads.VirtualThreads;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.TransactionPhase;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.util.Map;
import java.util.concurrent.ExecutorService;

@ApplicationScoped
public class ExecutionAttemptLauncher extends BaseAttemptLauncher<ExecutionAttemptEntity, ExecutionAttemptAO> {

    // Apps call back below these paths, see ExecutionAttemptWebsocketService and ExecutionAttemptService.
    public static final String TOOL_PATH = "execution/tool";
    public static final String LEARNING_PATH = "execution/learning";

    @Inject
    WorkflowNodeBO workflowNodeBO;

    @Inject
    WorkflowBO workflowBO;

    @Inject
    @RestClient
    GlobalAPIStoreService globalApi;

    @Inject
    PatientToolBO patientToolBO;

    @Inject
    FederatedLearningRunBO federatedLearningRunBO;

    @Inject
    WorkflowOrchestrator workflowOrchestrator;

    @Inject
    @VirtualThreads
    ExecutorService virtualThreads;

    // Tools reading or writing patient data and connector apps alike process data; a training learns across clinics.
    public ExecutionAttemptLauncher() {
        super(Map.of(
                ExecutionPurpose.PROCESSING, new ExecutionCallback(Scope.TOOL_RUN, TOOL_PATH),
                ExecutionPurpose.TRAINING, new ExecutionCallback(Scope.LEARNING_RUN, LEARNING_PATH)));
    }

    // A single tool runs like a node of its own: an app of the store, or the image a connector names.
    @Override
    protected WorkflowNodeDetailDTO targetOf(ExecutionAttemptEntity attempt) {
        ExecutionRunStepEntity step = attempt.getStep();
        if (step.getWorkflowNode() != null) {
            return workflowNodeBO.toDetailDto(step.getWorkflowNode());
        }
        WorkflowNodeDetailDTO node = new WorkflowNodeDetailDTO();
        node.setFederatedAppVersionId(step.getGlobalAppVersionId());
        FederatedAppDetailDTO app = step.getGlobalAppVersionId() != null
                ? globalApi.getAppByVersion(step.getGlobalAppVersionId())
                : new FederatedAppDetailDTO();
        if (step.getAppImage() != null) {
            app.setImageName(step.getAppImage());
        }
        if (app.getImageName() == null) {
            throw new IllegalStateException("Step " + step.getId() + " names no image to run");
        }
        node.setAppDetail(app);
        return node;
    }

    @Override
    protected WorkflowDTO workflowOf(ExecutionAttemptEntity attempt) {
        return workflowBO.entityToDto(attempt.getStep().getWorkflowNode().getWorkflow());
    }

    @Override
    protected void stageInputs(ExecutionAttemptEntity attempt) {
        switch (attempt.getStep().getRun().getPurpose()) {
            case PROCESSING -> patientToolBO.stageInput(attempt);
            case TRAINING -> federatedLearningRunBO.stageInputs(attempt);
            default -> {
            }
        }
    }

    // Patient tools leave what they wrote in their volume, every other app uploads it.
    @Override
    protected void customizeStart(ExecutionAttemptEntity attempt, StartWorkflowNodeDTO start) {
        start.setEnableRemoteResultSaving(attempt.getStep().getRun().getToolType() == null);
    }

    // A run that ended needs its containers and volumes no more, what it keeps was stored before.
    void onRunFinished(@Observes(during = TransactionPhase.AFTER_SUCCESS) RunFinishedEvent event) {
        virtualThreads.execute(() -> {
            try {
                workflowOrchestrator.cleanup(event.getRunId());
            } catch (RuntimeException e) {
                Log.warnf(e, "Could not clean up the containers of run %d", event.getRunId());
            }
        });
    }

    @Incoming(ExecutionChannels.ATTEMPT_LAUNCH)
    @RunOnVirtualThread
    public void consumeAttemptLaunch(AttemptLaunchEvent event) {
        onAttemptLaunch(event);
    }

    @Incoming(ExecutionChannels.ATTEMPT_FINISHED)
    @RunOnVirtualThread
    public void consumeAttemptFinished(AttemptFinishedEvent event) {
        onAttemptFinished(event);
    }
}
