package de.unihamburg.daibetes.execution.message;

import bio.cosy.feddb.core.execution.message.BaseExecutionMessageBO;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ExecutionMessageBO extends BaseExecutionMessageBO<ExecutionMessageEntity, ExecutionMessageAO, ExecutionMessageMapper> {
}
