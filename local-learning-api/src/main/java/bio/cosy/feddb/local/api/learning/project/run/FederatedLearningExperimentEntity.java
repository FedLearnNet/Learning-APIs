package bio.cosy.feddb.local.api.learning.project.run;

import bio.cosy.feddb.core.api.workflow.base.experiment.BaseWorkflowExperimentEntity;
import bio.cosy.feddb.local.api.learning.project.FederatedLearningProjectEntity;
import bio.cosy.feddb.local.api.learning.project.run.step.FederatedLearningExperimentStepEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@Entity
@Table(name = "federated_learning_experiments",
        indexes = {
                @Index(name = "idx_federated_learning_experiments_project_id", columnList = "project_id")
        })
public class FederatedLearningExperimentEntity extends BaseWorkflowExperimentEntity<FederatedLearningExperimentStepEntity> {

    @Column(name = "unique_random_clinic_id")
    private String uniqueRandomClinicId;

    @OneToOne(cascade = {CascadeType.MERGE, CascadeType.REFRESH})
    @JoinColumn(name = "project_id", nullable = false)
    private FederatedLearningProjectEntity project;
}
