package de.unihamburg.daibetes.execution.attempt;

import bio.cosy.feddb.core.execution.attempt.BaseExecutionAttemptEntity;
import de.unihamburg.daibetes.execution.message.ExecutionMessageEntity;
import de.unihamburg.daibetes.execution.step.ExecutionRunStepEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@Entity
@Table(name = "execution_attempt", uniqueConstraints = @UniqueConstraint(columnNames = {"step_id", "trial_index"}))
public class ExecutionAttemptEntity extends BaseExecutionAttemptEntity<ExecutionRunStepEntity, ExecutionMessageEntity> {

    // Result the app uploaded with its outputs.
    @Column(columnDefinition = "TEXT")
    private String result;
}
