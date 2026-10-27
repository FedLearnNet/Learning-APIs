package de.unihamburg.daibetes.execution.coordinator;

import bio.cosy.feddb.core.execution.coordinator.BaseStepCoordinator;
import bio.cosy.feddb.core.execution.event.ExecutionChannels;
import bio.cosy.feddb.core.execution.event.StepChangedEvent;
import de.unihamburg.daibetes.execution.attempt.ExecutionAttemptAO;
import de.unihamburg.daibetes.execution.attempt.ExecutionAttemptDTO;
import de.unihamburg.daibetes.execution.attempt.ExecutionAttemptEntity;
import de.unihamburg.daibetes.execution.attempt.ExecutionAttemptMapper;
import de.unihamburg.daibetes.execution.step.ExecutionRunStepAO;
import de.unihamburg.daibetes.execution.step.ExecutionRunStepEntity;
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
        onStepChangedTransactional(event);
    }
}
