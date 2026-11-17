package bio.cosy.feddb.core.execution.run;

import bio.cosy.feddb.core.base.BaseEntity;
import bio.cosy.feddb.core.execution.BaseExecutionStatusEntity;
import bio.cosy.feddb.core.execution.ExecutionLocation;
import bio.cosy.feddb.core.execution.ExecutionMode;
import bio.cosy.feddb.core.execution.ExecutionPurpose;
import bio.cosy.feddb.core.execution.run.types.RunStatusTypes;
import bio.cosy.feddb.core.execution.step.BaseExecutionRunStepEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.*;

@Getter
@Setter
@MappedSuperclass
public class BaseExecutionRunEntity<Step extends BaseExecutionRunStepEntity<?, ?, ?>> extends BaseExecutionStatusEntity {

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


    // The child's run relationship owns the foreign key; set both sides when adding a step.
    // ID order is stable presentation order, not the workflow's dependency/execution plan.
    @OneToMany(mappedBy = "run", fetch = FetchType.LAZY,
            cascade = {CascadeType.PERSIST, CascadeType.MERGE, CascadeType.REMOVE})
    @OrderBy("id ASC")
    private List<Step> steps = new ArrayList<>();

    public List<Step> getStepsInExecutionOrder() {
        return steps.stream()
                .sorted(Comparator
                        .comparing((Step step) -> step.getWorkflowNode() == null ? null : step.getWorkflowNode().getExecutionOrder(),
                                Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(BaseEntity::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    public Optional<Step> getCurrentStep() {
        return getStepsInExecutionOrder().stream()
                .filter(step -> step.getStatus() != RunStatusTypes.PENDING)
                .reduce((earlier, later) -> later);
    }
}
