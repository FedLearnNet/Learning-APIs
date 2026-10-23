package bio.cosy.feddb.core.execution.run;

import bio.cosy.feddb.core.base.BaseEntity;
import bio.cosy.feddb.core.execution.ExecutionLocation;
import bio.cosy.feddb.core.execution.ExecutionMode;
import bio.cosy.feddb.core.execution.ExecutionPurpose;
import bio.cosy.feddb.core.execution.failure.ExecutionFailure;
import bio.cosy.feddb.core.execution.run.types.RunStatusTypes;
import bio.cosy.feddb.core.execution.step.BaseExecutionRunStepEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@MappedSuperclass
public class BaseExecutionRunEntity<Step extends BaseExecutionRunStepEntity<?, ?, ?>> extends BaseEntity {

    @Column(name = "public_id", nullable = false, unique = true, updatable = false)
    private UUID publicId;

    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private ExecutionPurpose purpose;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private ExecutionMode mode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private ExecutionLocation location;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RunStatusTypes status = RunStatusTypes.PENDING;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "failure", columnDefinition = "jsonb")
    private ExecutionFailure failure;

    // The child's run relationship owns the foreign key; set both sides when adding a step.
    // ID order is stable presentation order, not the workflow's dependency/execution plan.
    @OneToMany(mappedBy = "run", fetch = FetchType.LAZY,
            cascade = {CascadeType.PERSIST, CascadeType.MERGE, CascadeType.REMOVE})
    @OrderBy("id ASC")
    private List<Step> steps = new ArrayList<>();
}
