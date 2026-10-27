package bio.cosy.feddb.local.execution.coordinator;

import bio.cosy.feddb.core.execution.coordinator.BaseStepCoordinator;
import bio.cosy.feddb.core.execution.event.ExecutionChannels;
import bio.cosy.feddb.core.execution.event.StepChangedEvent;
import bio.cosy.feddb.local.execution.attempt.ExecutionAttemptAO;
import bio.cosy.feddb.local.execution.attempt.ExecutionAttemptDTO;
import bio.cosy.feddb.local.execution.attempt.ExecutionAttemptEntity;
import bio.cosy.feddb.local.execution.attempt.ExecutionAttemptMapper;
import bio.cosy.feddb.local.execution.step.ExecutionRunStepAO;
import bio.cosy.feddb.local.execution.step.ExecutionRunStepEntity;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.reactive.messaging.Incoming;

@ApplicationScoped
public class ExecutionStepCoordinator extends BaseStepCoordinator<ExecutionRunStepEntity, ExecutionAttemptEntity,
        ExecutionAttemptDTO, ExecutionAttemptMapper, ExecutionAttemptAO, ExecutionRunStepAO> {

    public ExecutionStepCoordinator() {
        super(ExecutionAttemptEntity::new);
    }

    @Incoming(ExecutionChannels.STEP_CHANGED)
    @RunOnVirtualThread
    public void consume(StepChangedEvent event) {
        onStepChanged(event);
    }
}
