package de.unihamburg.daibetes.execution.coordinator;

import bio.cosy.feddb.core.execution.coordinator.BaseWorkflowCoordinator;
import bio.cosy.feddb.core.execution.event.ExecutionChannels;
import bio.cosy.feddb.core.execution.event.RunStartedEvent;
import bio.cosy.feddb.core.execution.event.StepFinishedEvent;
import de.unihamburg.daibetes.execution.run.ExecutionRunAO;
import de.unihamburg.daibetes.execution.run.ExecutionRunEntity;
import de.unihamburg.daibetes.execution.step.ExecutionRunStepAO;
import de.unihamburg.daibetes.execution.step.ExecutionRunStepEntity;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.reactive.messaging.Incoming;

@ApplicationScoped
public class ExecutionWorkflowCoordinator extends BaseWorkflowCoordinator<ExecutionRunEntity, ExecutionRunStepEntity,
        ExecutionRunAO, ExecutionRunStepAO> {

    @Incoming(ExecutionChannels.RUN_STARTED)
    @RunOnVirtualThread
    public void consumeRunStarted(RunStartedEvent event) {
        onRunStarted(event);
    }

    @Incoming(ExecutionChannels.STEP_FINISHED)
    @RunOnVirtualThread
    public void consumeStepFinished(StepFinishedEvent event) {
        onStepFinished(event);
    }
}
