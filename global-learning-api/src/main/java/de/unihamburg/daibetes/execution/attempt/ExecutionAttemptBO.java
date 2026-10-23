package de.unihamburg.daibetes.execution.attempt;

import bio.cosy.feddb.core.execution.attempt.BaseExecutionAttemptBO;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ExecutionAttemptBO extends BaseExecutionAttemptBO<ExecutionAttemptDTO, ExecutionAttemptEntity, ExecutionAttemptAO, ExecutionAttemptMapper> {
}
