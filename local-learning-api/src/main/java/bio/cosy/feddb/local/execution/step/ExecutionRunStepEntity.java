package bio.cosy.feddb.local.execution.step;

import bio.cosy.feddb.core.execution.step.BaseExecutionRunStepEntity;
import bio.cosy.feddb.local.api.importer.transformer.ConnectorTransformerEntity;
import bio.cosy.feddb.local.api.workflow.node.WorkflowNodeEntity;
import bio.cosy.feddb.local.execution.attempt.ExecutionAttemptEntity;
import bio.cosy.feddb.local.execution.federation.ExecutionFederationEntity;
import bio.cosy.feddb.local.execution.run.ExecutionRunEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@Entity
@Table(name = "execution_run_step", uniqueConstraints = @UniqueConstraint(columnNames = {"run_id", "workflow_node_id"}))
public class ExecutionRunStepEntity extends BaseExecutionRunStepEntity<ExecutionRunEntity, WorkflowNodeEntity, ExecutionAttemptEntity> {

    @Column(name = "global_model_version_id", updatable = false)
    private Long globalModelVersionId;

    @Column(name = "global_app_version_id", updatable = false)
    private Long globalAppVersionId;

    @Column(name = "app_image", updatable = false)
    private String appImage;

    @Column(name = "app_name", updatable = false)
    private String appName;

    // If run is a connector
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transformer_id")
    private ConnectorTransformerEntity transformer;

    //if run is a fl run
    @OneToOne(mappedBy = "step", cascade = CascadeType.ALL, orphanRemoval = true)
    private ExecutionFederationEntity federation;

    public ExecutionFederationEntity federationOrNew() {
        if (federation == null) {
            federation = new ExecutionFederationEntity();
            federation.setStep(this);
        }
        return federation;
    }
}
