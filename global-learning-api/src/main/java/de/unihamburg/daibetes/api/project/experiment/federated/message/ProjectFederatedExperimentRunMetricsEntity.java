package de.unihamburg.daibetes.api.project.experiment.federated.message;

import de.unihamburg.daibetes.api.project.experiment.federated.step.ProjectFederatedExperimentStepEntity;
import bio.cosy.feddb.core.base.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@Entity
@Table(name = "project_federated_experiments_run_metrics")
public class ProjectFederatedExperimentRunMetricsEntity extends BaseEntity {

    @Column(columnDefinition="TEXT")
    private String message;

    @ManyToOne(cascade = CascadeType.REFRESH)
    @JoinColumn(name = "experiment_step_id", nullable = false)
    private ProjectFederatedExperimentStepEntity step;

}
