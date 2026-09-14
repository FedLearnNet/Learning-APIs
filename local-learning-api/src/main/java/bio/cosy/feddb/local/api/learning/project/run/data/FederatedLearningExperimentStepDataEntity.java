package bio.cosy.feddb.local.api.learning.project.run.data;

import bio.cosy.feddb.core.base.BaseEntity;
import bio.cosy.feddb.local.api.file.FileEntity;
import bio.cosy.feddb.local.api.learning.project.run.step.FederatedLearningExperimentStepEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@Entity
@Table(name = "federated_learning_experiments_steps_data",
        indexes = {
                @Index(name = "idx_federated_learning_experiments_steps_data_file_id", columnList = "file_id"),
                @Index(name = "idx_federated_learning_experiments_steps_data_step_input_id", columnList = "step_input_id"),
                @Index(name = "idx_federated_learning_experiments_steps_data_step_output_id", columnList = "step_output_id")
        })
public class FederatedLearningExperimentStepDataEntity extends BaseEntity {
    //for v2
    @Column(columnDefinition = "TEXT")
    private String result;

    private String name;

    @ManyToOne(cascade = CascadeType.REFRESH)
    @JoinColumn(name = "file_id")
    private FileEntity file;

    @ManyToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "step_input_id")
    private FederatedLearningExperimentStepEntity stepInput;

    @ManyToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "step_output_id")
    private FederatedLearningExperimentStepEntity stepOutput;
}
