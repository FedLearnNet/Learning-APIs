package bio.cosy.feddb.core.execution.attempt;

import bio.cosy.feddb.core.execution.BaseExecutionStatusEntity;
import bio.cosy.feddb.core.execution.message.BaseExecutionMessageEntity;
import bio.cosy.feddb.core.execution.run.dto.RunMetaDTO;
import bio.cosy.feddb.core.execution.step.BaseExecutionRunStepEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.LinkedHashMap;
import java.util.UUID;

/**
 * Shared physical attempt mapping. Concrete tables should enforce uniqueness on
 * (step_id, trial_index). Local and Global bind Step and Message to their concrete entities.
 * A step's hyper parameter search runs one attempt per parameter set, trial_index naming the set.
 */
@Getter
@Setter
@MappedSuperclass
public abstract class BaseExecutionAttemptEntity<
        Step extends BaseExecutionRunStepEntity<?, ?, ?>,
        Message extends BaseExecutionMessageEntity> extends BaseExecutionStatusEntity {

    @Column(name = "public_id", nullable = false, unique = true, updatable = false)
    private UUID publicId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "step_id", nullable = false, updatable = false)
    private Step step;

    @Column(name = "trial_index", nullable = false, updatable = false)
    private int trialIndex;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "params", columnDefinition = "jsonb", updatable = false)
    private LinkedHashMap<String, Object> params;

    // Latest message of the search metric the attempt reported, which ranks its trial; null when none.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "score_message_id")
    private Message scoreMessage;


    @Column(name = "container_id")
    private String containerId;

    // Percentage in the range 0..100, not a fraction.
    private Float progress;


    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "meta", columnDefinition = "jsonb")
    private RunMetaDTO meta;
}
