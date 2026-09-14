package bio.cosy.feddb.local.api.learning.project.run.step;

import bio.cosy.feddb.core.api.socket.FederatedLearningRelayInfoDTO;
import bio.cosy.feddb.core.api.workflow.base.step.BaseWorkflowStepEntity;
import bio.cosy.feddb.local.api.learning.project.run.FederatedLearningExperimentEntity;
import bio.cosy.feddb.local.api.learning.project.run.data.FederatedLearningExperimentStepDataEntity;
import bio.cosy.feddb.local.api.workflow.node.WorkflowNodeEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.OptimisticLockType;
import org.hibernate.annotations.OptimisticLocking;
import org.hibernate.type.SqlTypes;

import java.util.Set;

@Setter
@Getter
@Entity
@Table(name = "federated_learning_experiments_steps")
@OptimisticLocking(type = OptimisticLockType.NONE)
public class FederatedLearningExperimentStepEntity extends BaseWorkflowStepEntity<WorkflowNodeEntity, FederatedLearningExperimentEntity> {

    @OneToMany(mappedBy = "stepOutput", cascade = CascadeType.ALL)
    private Set<FederatedLearningExperimentStepDataEntity> results;

    @OneToMany(mappedBy = "stepInput", cascade = CascadeType.ALL)
    private Set<FederatedLearningExperimentStepDataEntity> inputs;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "relay_info", columnDefinition = "jsonb")
    private FederatedLearningRelayInfoDTO relayInfo;

    @Override
    public String toString() {
        return "FederatedLearningExperimentStepEntity{" +
                "stepStatus=" + getStepStatus() +
                ", containerId='" + getContainerId() + '\'' +
                ", lastError='" + getLastError() + '\'' +
                ", progress=" + getProgress() +
                ", workflowNode=" + getWorkflowNode().getId() +
                ", experiment=" + getExperiment().getId() +
                '}';
    }
}
