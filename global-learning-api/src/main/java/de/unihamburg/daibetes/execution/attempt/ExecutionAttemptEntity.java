package de.unihamburg.daibetes.execution.attempt;

import bio.cosy.feddb.core.execution.attempt.BaseExecutionAttemptEntity;
import de.unihamburg.daibetes.execution.message.ExecutionMessageEntity;
import de.unihamburg.daibetes.execution.step.ExecutionRunStepEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "execution_attempt", uniqueConstraints = @UniqueConstraint(columnNames = {"step_id", "trial_index"}))
public class ExecutionAttemptEntity extends BaseExecutionAttemptEntity<ExecutionRunStepEntity, ExecutionMessageEntity> {
}
