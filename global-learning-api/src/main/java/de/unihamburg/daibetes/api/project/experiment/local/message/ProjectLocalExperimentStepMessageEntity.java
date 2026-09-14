package de.unihamburg.daibetes.api.project.experiment.local.message;

import bio.cosy.feddb.core.api.run.message.BaseRunMessageEntity;
import de.unihamburg.daibetes.api.project.experiment.local.step.ProjectLocalExperimentStepEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@Entity
@Table(name = "project_local_experiments_step_messages")
public class ProjectLocalExperimentStepMessageEntity extends BaseRunMessageEntity {

    @ManyToOne(cascade = CascadeType.REFRESH)
    @JoinColumn(name = "experiment_step_id", nullable = false)
    private ProjectLocalExperimentStepEntity step;

}
