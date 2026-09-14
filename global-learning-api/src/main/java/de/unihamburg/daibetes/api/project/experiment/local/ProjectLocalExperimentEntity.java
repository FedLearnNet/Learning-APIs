package de.unihamburg.daibetes.api.project.experiment.local;

import bio.cosy.feddb.core.api.project.ProjectStatus;
import bio.cosy.feddb.core.api.workflow.base.experiment.BaseWorkflowExperimentEntity;
import bio.cosy.feddb.core.base.BaseEntity;
import de.unihamburg.daibetes.api.project.ProjectEntity;
import de.unihamburg.daibetes.api.project.experiment.local.message.ProjectLocalExperimentStepMessageEntity;
import de.unihamburg.daibetes.api.project.experiment.local.step.ProjectLocalExperimentStepEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.Date;
import java.util.Set;

@Setter
@Getter
@Entity
@Table(name = "project_local_experiments")
public class ProjectLocalExperimentEntity extends BaseWorkflowExperimentEntity<ProjectLocalExperimentStepEntity> {

    //UUID generated in the business layer, not shown to user;
    private String groupId;

    private String name;
    private String description;

    @Column(name = "is_test_run")
    private boolean isTestRun;

    @Column(columnDefinition = "TEXT", name = "diagram_config")
    private String diagramConfig;

    @ManyToOne(cascade = {CascadeType.MERGE, CascadeType.REFRESH})
    @JoinColumn(name = "project_id", nullable = false)
    private ProjectEntity project;
}
