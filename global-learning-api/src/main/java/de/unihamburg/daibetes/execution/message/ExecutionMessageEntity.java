package de.unihamburg.daibetes.execution.message;

import bio.cosy.feddb.core.execution.message.BaseExecutionMessageEntity;
import de.unihamburg.daibetes.execution.attempt.ExecutionAttemptEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@Entity
@Table(name = "execution_message")
public class ExecutionMessageEntity extends BaseExecutionMessageEntity {

    @ManyToOne(cascade = CascadeType.REFRESH)
    @JoinColumn(name = "attempt_id", nullable = false)
    private ExecutionAttemptEntity attempt;
}
