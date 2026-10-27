package de.unihamburg.daibetes.execution.run;

import bio.cosy.feddb.core.execution.event.BaseExecutionRunSender;
import de.unihamburg.daibetes.execution.attempt.ExecutionAttemptAO;
import de.unihamburg.daibetes.execution.step.ExecutionRunStepAO;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ExecutionRunSender extends BaseExecutionRunSender<ExecutionRunAO, ExecutionRunStepAO, ExecutionAttemptAO> {
}
