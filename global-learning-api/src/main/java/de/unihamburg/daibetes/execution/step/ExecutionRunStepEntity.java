package de.unihamburg.daibetes.execution.step;

import bio.cosy.feddb.core.execution.step.BaseExecutionRunStepEntity;
import de.unihamburg.daibetes.api.workflow.node.WorkflowNodeEntity;
import de.unihamburg.daibetes.execution.attempt.ExecutionAttemptEntity;
import de.unihamburg.daibetes.execution.run.ExecutionRunEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "execution_run_step", uniqueConstraints = @UniqueConstraint(columnNames = {"run_id", "workflow_node_id"}))
public class ExecutionRunStepEntity extends BaseExecutionRunStepEntity<ExecutionRunEntity, WorkflowNodeEntity, ExecutionAttemptEntity> {
}
