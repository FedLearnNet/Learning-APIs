package bio.cosy.feddb.core.execution;

import bio.cosy.feddb.core.base.BaseEntity;
import bio.cosy.feddb.core.execution.failure.ExecutionFailure;
import bio.cosy.feddb.core.execution.run.types.RunStatusTypes;
import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Getter
@Setter
@MappedSuperclass
public abstract class BaseExecutionStatusEntity extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private RunStatusTypes status = RunStatusTypes.PENDING;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "failure", columnDefinition = "jsonb")
    private ExecutionFailure failure;

    public void begin(RunStatusTypes next) {
        status = next;
        if (startedAt == null) {
            startedAt = Instant.now();
        }
    }

    public void end(RunStatusTypes last, ExecutionFailure cause) {
        status = last;
        finishedAt = Instant.now();
        failure = cause;
    }

    public void moveTo(RunStatusTypes next) {
        if (RunStatusTypes.isFinalStatus(next)) {
            end(next, failure);
        } else {
            begin(next);
        }
    }

    public boolean hasEnded() {
        return RunStatusTypes.isFinalStatus(status);
    }
}
