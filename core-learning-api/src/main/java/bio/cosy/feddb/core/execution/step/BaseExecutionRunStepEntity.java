package bio.cosy.feddb.core.execution.step;

import bio.cosy.feddb.core.api.workflow.node.BaseWorkflowNodeEntity;
import bio.cosy.feddb.core.base.BaseEntity;
import bio.cosy.feddb.core.execution.attempt.BaseExecutionAttemptEntity;
import bio.cosy.feddb.core.execution.failure.ExecutionFailure;
import bio.cosy.feddb.core.execution.hyperparam.config.HyperParamSearchDTO;
import bio.cosy.feddb.core.execution.run.BaseExecutionRunEntity;
import bio.cosy.feddb.core.execution.run.types.RunStatusTypes;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Shared step mapping. Concrete tables should enforce uniqueness on (run_id, workflow_node_id).
 * The workflow node must be a versioned/snapshotted definition, not a mutable editor node.
 */
@Getter
@Setter
@MappedSuperclass
public abstract class BaseExecutionRunStepEntity<
        Run extends BaseExecutionRunEntity<?>,
        Node extends BaseWorkflowNodeEntity,
        Attempt extends BaseExecutionAttemptEntity<?, ?>> extends BaseEntity {

    @Column(name = "public_id", nullable = false, unique = true, updatable = false)
    private UUID publicId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "run_id", nullable = false, updatable = false)
    private Run run;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "workflow_node_id", nullable = false, updatable = false)
    private Node workflowNode;

    @Column(name = "name")
    private String name;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private RunStatusTypes status = RunStatusTypes.PENDING;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "failure", columnDefinition = "jsonb")
    private ExecutionFailure failure;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "search", columnDefinition = "jsonb", updatable = false)
    private HyperParamSearchDTO search;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "selected_attempt_id")
    private Attempt selectedAttempt;

    @OneToMany(mappedBy = "step", fetch = FetchType.LAZY,
            cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    @OrderBy("trialIndex ASC")
    private Set<Attempt> attempts = new LinkedHashSet<>();
}
