package de.unihamburg.daibetes.execution.coordinator;

import bio.cosy.feddb.core.api.workflow.WorkflowDTO;
import bio.cosy.feddb.core.api.workflow.node.WorkflowNodeDetailDTO;
import bio.cosy.feddb.core.execution.ExecutionPurpose;
import bio.cosy.feddb.core.execution.coordinator.BaseAttemptLauncher;
import bio.cosy.feddb.core.execution.coordinator.ExecutionCallback;
import bio.cosy.feddb.core.execution.event.AttemptFinishedEvent;
import bio.cosy.feddb.core.execution.event.AttemptLaunchEvent;
import bio.cosy.feddb.core.execution.event.ExecutionChannels;
import bio.cosy.feddb.core.security.Scope;
import bio.cosy.feddb.core.services.orch.dto.StartWorkflowNodeDTO;
import de.unihamburg.daibetes.api.workflow.node.WorkflowNodeEntity;
import de.unihamburg.daibetes.api.workflow.node.WorkflowNodeMapper;
import de.unihamburg.daibetes.execution.attempt.ExecutionAttemptAO;
import de.unihamburg.daibetes.execution.attempt.ExecutionAttemptEntity;
import de.unihamburg.daibetes.execution.step.ExecutionRunStepEntity;
import io.smallrye.common.annotation.RunOnVirtualThread;
import de.unihamburg.daibetes.api.project.training.platform.PlatformTrainingBO;
import de.unihamburg.daibetes.api.workflow.WorkflowBO;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Incoming;

import java.util.Map;

@ApplicationScoped
public class ExecutionAttemptLauncher extends BaseAttemptLauncher<ExecutionAttemptEntity, ExecutionAttemptAO> {

    @Inject
    WorkflowNodeMapper workflowNodeMapper;

    @Inject
    WorkflowBO workflowBO;

    @Inject
    PlatformTrainingBO platformTrainingBO;

    // The apps talk back through the existing BO and websocket of their purpose.
    public ExecutionAttemptLauncher() {
        super(Map.of(
                ExecutionPurpose.ANALYSIS, new ExecutionCallback(Scope.MODEL_WORKFLOW_RUN, "workflow"),
                ExecutionPurpose.TRAINING, new ExecutionCallback(Scope.MODEL_WORKFLOW_RUN, "workflow")));
    }

    @Override
    protected WorkflowNodeDetailDTO targetOf(ExecutionAttemptEntity attempt) {
        ExecutionRunStepEntity step = attempt.getStep();
        WorkflowNodeEntity node = step.getWorkflowNode();
        if (node == null) {
            // A single app or model runs like a node of its own; built in memory only, never stored.
            node = new WorkflowNodeEntity();
            node.setFederatedAppVersion(step.getFederatedAppVersion());
            node.setSubModel(step.getSubModel());
        }
        if (node.getFederatedAppVersion() == null && node.getSubModel() == null) {
            throw new IllegalStateException("Step " + step.getId() + " names neither an app nor a model to run");
        }
        return workflowNodeMapper.entityToDto(node);
    }

    @Override
    protected WorkflowDTO workflowOf(ExecutionAttemptEntity attempt) {
        return workflowBO.entityToDto(attempt.getStep().getWorkflowNode().getWorkflow());
    }

    @Override
    protected void stageInputs(ExecutionAttemptEntity attempt) {
        if (attempt.getStep().getRun().getPurpose() == ExecutionPurpose.TRAINING) {
            platformTrainingBO.stageInputs(attempt);
        }
    }

    // An analysis uploads its outputs, a training leaves them in its volume until its step keeps them.
    @Override
    protected void customizeStart(ExecutionAttemptEntity attempt, StartWorkflowNodeDTO start) {
        start.setEnableRemoteResultSaving(attempt.getStep().getRun().getPurpose() == ExecutionPurpose.ANALYSIS);
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
