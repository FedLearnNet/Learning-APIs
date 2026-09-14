package bio.cosy.feddb.core.api.workflow.base.step;

import bio.cosy.feddb.core.api.run.RunMetaDTO;
import bio.cosy.feddb.core.api.run.RunStatusTypes;
import bio.cosy.feddb.core.api.workflow.base.experiment.BaseWorkflowExperimentEntity;
import bio.cosy.feddb.core.api.workflow.node.BaseWorkflowNodeEntity;
import bio.cosy.feddb.core.base.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Setter
@Getter
@MappedSuperclass
public class BaseWorkflowStepEntity<T extends BaseWorkflowNodeEntity, E extends BaseWorkflowExperimentEntity<?>> extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "step_status")
    private RunStatusTypes stepStatus;

    @Column(name = "container_id", nullable = true)
    private String containerId;

    // Run metadata (timings + reserved fields) as JSON metadata, nullable for historical steps.
    // RUNTIME always stored; OVERHEAD_* only when posymed.runtime.overhead.enabled is true.
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "meta", columnDefinition = "jsonb")
    private RunMetaDTO meta;

    @Column(length = 2000, name = "last_error")
    private String lastError;

    @Column(name = "progress", nullable = true)
    private Float progress;

    @ManyToOne(cascade = {CascadeType.MERGE, CascadeType.REFRESH})
    @JoinColumn(name = "workflow_node_id", nullable = false)
    private T workflowNode;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "experiment_id", nullable = false)
    private E experiment;

    @Override
    public String toString() {
        return "BaseWorkflowStepEntity{" +
                "stepStatus=" + stepStatus +
                ", containerId='" + containerId + '\'' +
                ", lastError='" + lastError + '\'' +
                ", progress=" + progress +
                ", workflowNode=" + workflowNode +
                '}';
    }
}
