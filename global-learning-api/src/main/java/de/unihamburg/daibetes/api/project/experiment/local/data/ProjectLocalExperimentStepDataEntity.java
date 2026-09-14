package de.unihamburg.daibetes.api.project.experiment.local.data;

import bio.cosy.feddb.core.base.BaseEntity;
import de.unihamburg.daibetes.api.file.FileEntity;
import de.unihamburg.daibetes.api.project.experiment.local.step.ProjectLocalExperimentStepEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@Entity
@Table(name = "project_local_experiments_step_results")
public class ProjectLocalExperimentStepDataEntity extends BaseEntity {

    //for v2
    @Column(columnDefinition = "TEXT")
    private String result;

    private String name;

    @ManyToOne(cascade = CascadeType.REFRESH)
    @JoinColumn(name = "file_id")
    private FileEntity file;

    @ManyToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "step_input_id")
    private ProjectLocalExperimentStepEntity stepInput;

    @ManyToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "step_output_id")
    private ProjectLocalExperimentStepEntity stepOutput;
}
