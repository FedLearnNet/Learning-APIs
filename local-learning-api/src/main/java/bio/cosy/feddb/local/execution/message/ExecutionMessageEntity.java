package bio.cosy.feddb.local.execution.message;

import bio.cosy.feddb.core.execution.message.BaseExecutionMessageEntity;
import bio.cosy.feddb.local.execution.attempt.ExecutionAttemptEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

@Setter
@Getter
@Entity
@Table(name = "execution_message", indexes = @Index(name = "idx_execution_message_attempt_id", columnList = "attempt_id"))
public class ExecutionMessageEntity extends BaseExecutionMessageEntity {

    @ManyToOne(cascade = CascadeType.REFRESH)
    @JoinColumn(name = "attempt_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private ExecutionAttemptEntity attempt;
}
