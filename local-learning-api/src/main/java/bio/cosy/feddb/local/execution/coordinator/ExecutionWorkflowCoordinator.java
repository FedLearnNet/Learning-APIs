package bio.cosy.feddb.local.execution.coordinator;

import bio.cosy.feddb.core.execution.coordinator.BaseWorkflowCoordinator;
import bio.cosy.feddb.core.execution.event.ExecutionChannels;
import bio.cosy.feddb.core.execution.event.RunStartedEvent;
import bio.cosy.feddb.core.execution.event.StepFinishedEvent;
import bio.cosy.feddb.local.execution.run.ExecutionRunAO;
import bio.cosy.feddb.local.execution.run.ExecutionRunEntity;
import bio.cosy.feddb.local.execution.step.ExecutionRunStepAO;
import bio.cosy.feddb.local.execution.step.ExecutionRunStepEntity;
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
