package bio.cosy.feddb.local.api.learning.project.run.message;

import bio.cosy.feddb.core.api.run.message.BaseRunMessageEntity;
import bio.cosy.feddb.core.api.run.message.RunMessageTypes;
import bio.cosy.feddb.core.base.BaseEntity;
import bio.cosy.feddb.local.api.learning.project.run.step.FederatedLearningExperimentStepEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@Entity
@Table(name = "federated_learning_experiments_steps_messages",
        indexes = {
                @Index(name = "idx_federated_learning_experiments_steps_messages_step_id", columnList = "step_id")
        })
public class FederatedLearningExperimentStepMessageEntity extends BaseRunMessageEntity {

    @ManyToOne(cascade = CascadeType.REFRESH)
    @JoinColumn(name = "step_id", nullable = false)
    private FederatedLearningExperimentStepEntity step;

}
