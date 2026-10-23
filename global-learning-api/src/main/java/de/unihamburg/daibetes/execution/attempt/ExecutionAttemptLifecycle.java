package de.unihamburg.daibetes.execution.attempt;

import bio.cosy.feddb.core.execution.attempt.BaseAttemptLifecycle;
import de.unihamburg.daibetes.execution.message.ExecutionMessageAO;
import de.unihamburg.daibetes.execution.message.ExecutionMessageEntity;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ExecutionAttemptLifecycle extends BaseAttemptLifecycle<ExecutionAttemptEntity, ExecutionAttemptAO,
        ExecutionMessageEntity, ExecutionMessageAO> {

    public ExecutionAttemptLifecycle() {
        super(attempt -> {
            ExecutionMessageEntity message = new ExecutionMessageEntity();
            message.setAttempt(attempt);
            return message;
        });
    }
}
