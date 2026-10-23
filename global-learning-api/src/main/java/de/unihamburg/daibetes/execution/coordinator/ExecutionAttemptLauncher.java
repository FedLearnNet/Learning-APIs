package de.unihamburg.daibetes.execution.coordinator;

import bio.cosy.feddb.core.api.model.prediction.DataAnalysisRunModesEnum;
import bio.cosy.feddb.core.api.workflow.node.WorkflowNodeDetailDTO;
import bio.cosy.feddb.core.execution.ExecutionPurpose;
import bio.cosy.feddb.core.execution.coordinator.BaseAttemptLauncher;
import bio.cosy.feddb.core.execution.coordinator.ExecutionCallback;
import bio.cosy.feddb.core.execution.event.AttemptFinishedEvent;
import bio.cosy.feddb.core.execution.event.AttemptLaunchEvent;
import bio.cosy.feddb.core.execution.event.ExecutionChannels;
import bio.cosy.feddb.core.execution.orchestrator.DataAnalysisOrchestrator;
import bio.cosy.feddb.core.security.Scope;
import de.unihamburg.daibetes.api.workflow.node.WorkflowNodeEntity;
import de.unihamburg.daibetes.api.workflow.node.WorkflowNodeMapper;
import de.unihamburg.daibetes.execution.attempt.ExecutionAttemptAO;
import de.unihamburg.daibetes.execution.attempt.ExecutionAttemptEntity;
import de.unihamburg.daibetes.execution.step.ExecutionRunStepEntity;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Incoming;

import java.util.Map;

@ApplicationScoped
public class ExecutionAttemptLauncher extends BaseAttemptLauncher<ExecutionAttemptEntity, ExecutionAttemptAO> {

    @Inject
    WorkflowNodeMapper workflowNodeMapper;

    // The apps talk back through the existing BO and websocket of their purpose.
    public ExecutionAttemptLauncher() {
        super(Map.of(
                ExecutionPurpose.ANALYSIS, new ExecutionCallback(Scope.MODEL_WORKFLOW_RUN,
                        DataAnalysisOrchestrator.getUrlForRunning(DataAnalysisRunModesEnum.WORKFLOW)),
                ExecutionPurpose.TRAINING, new ExecutionCallback(Scope.LOCAL_EXPERIMENT_RUN, "project/experiment/local")));
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
