package de.unihamburg.daibetes.execution.step;

import bio.cosy.feddb.core.execution.step.BaseExecutionRunStepEntity;
import de.unihamburg.daibetes.api.app.version.FederatedAppVersionEntity;
import de.unihamburg.daibetes.api.model.sub.ModelSubEntity;
import de.unihamburg.daibetes.api.workflow.node.WorkflowNodeEntity;
import de.unihamburg.daibetes.execution.attempt.ExecutionAttemptEntity;
import de.unihamburg.daibetes.execution.run.ExecutionRunEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@Entity
@Table(name = "execution_run_step", uniqueConstraints = @UniqueConstraint(columnNames = {"run_id", "workflow_node_id"}))
public class ExecutionRunStepEntity extends BaseExecutionRunStepEntity<ExecutionRunEntity, WorkflowNodeEntity, ExecutionAttemptEntity> {

    // What a step without a workflow node runs: an app version or a model sub.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "app_version_id", updatable = false)
    private FederatedAppVersionEntity federatedAppVersion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sub_model_id", updatable = false)
    private ModelSubEntity subModel;
}
