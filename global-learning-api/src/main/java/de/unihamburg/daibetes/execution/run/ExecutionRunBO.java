package de.unihamburg.daibetes.execution.run;

import bio.cosy.feddb.core.execution.run.BaseExecutionRunBO;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ExecutionRunBO extends BaseExecutionRunBO<ExecutionRunDTO, ExecutionRunEntity, ExecutionRunAO, ExecutionRunMapper> {
}
