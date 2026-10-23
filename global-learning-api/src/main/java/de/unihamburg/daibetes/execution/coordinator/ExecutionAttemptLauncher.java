package de.unihamburg.daibetes.execution.coordinator;

import bio.cosy.feddb.core.api.workflow.node.WorkflowNodeDetailDTO;
import bio.cosy.feddb.core.execution.coordinator.BaseAttemptLauncher;
import bio.cosy.feddb.core.execution.event.AttemptFinishedEvent;
import bio.cosy.feddb.core.execution.event.AttemptLaunchEvent;
import bio.cosy.feddb.core.execution.event.ExecutionChannels;
import de.unihamburg.daibetes.api.workflow.node.WorkflowNodeMapper;
import de.unihamburg.daibetes.execution.attempt.ExecutionAttemptAO;
import de.unihamburg.daibetes.execution.attempt.ExecutionAttemptEntity;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Incoming;

@ApplicationScoped
public class ExecutionAttemptLauncher extends BaseAttemptLauncher<ExecutionAttemptEntity, ExecutionAttemptAO> {

    @Inject
    WorkflowNodeMapper workflowNodeMapper;

    @Override
    protected WorkflowNodeDetailDTO nodeDetailOf(ExecutionAttemptEntity attempt) {
        return workflowNodeMapper.entityToDto(attempt.getStep().getWorkflowNode());
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
