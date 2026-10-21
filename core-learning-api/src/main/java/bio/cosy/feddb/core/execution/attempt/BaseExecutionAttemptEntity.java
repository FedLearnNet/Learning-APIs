package bio.cosy.feddb.core.execution.attempt;

import bio.cosy.feddb.core.base.BaseEntity;
import bio.cosy.feddb.core.execution.failure.ExecutionFailure;
import bio.cosy.feddb.core.execution.run.dto.RunMetaDTO;
import bio.cosy.feddb.core.execution.run.types.RunStatusTypes;
import bio.cosy.feddb.core.execution.step.BaseExecutionRunStepEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * Shared physical attempt mapping. Concrete tables should enforce uniqueness on
 * (step_id, attempt_number). Local and Global bind Step to their concrete step entity.
 */
@Getter
@Setter
@MappedSuperclass
public abstract class BaseExecutionAttemptEntity<Step extends BaseExecutionRunStepEntity<?, ?, ?>>
        extends BaseEntity {

    @Column(name = "public_id", nullable = false, unique = true, updatable = false)
    private UUID publicId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "step_id", nullable = false, updatable = false)
    private Step step;

    @Column(name = "attempt_number", nullable = false, updatable = false)
    private int attemptNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RunStatusTypes status = RunStatusTypes.PENDING;

    @Column(name = "container_id")
    private String containerId;

    // Percentage in the range 0..100, not a fraction.
    private Float progress;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "failure", columnDefinition = "jsonb")
    private ExecutionFailure failure;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "meta", columnDefinition = "jsonb")
    private RunMetaDTO meta;
}
