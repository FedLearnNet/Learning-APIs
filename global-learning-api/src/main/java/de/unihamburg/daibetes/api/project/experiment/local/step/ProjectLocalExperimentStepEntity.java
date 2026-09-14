package de.unihamburg.daibetes.api.project.experiment.local.step;

import bio.cosy.feddb.core.api.workflow.base.step.BaseWorkflowStepEntity;
import de.unihamburg.daibetes.api.project.experiment.local.ProjectLocalExperimentEntity;
import de.unihamburg.daibetes.api.project.experiment.local.data.ProjectLocalExperimentStepDataEntity;
import de.unihamburg.daibetes.api.project.experiment.local.message.ProjectLocalExperimentStepMessageEntity;
import de.unihamburg.daibetes.api.workflow.node.WorkflowNodeEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.util.Set;

@Setter
@Getter
@Entity
@Table(name = "project_local_experiments_steps")
public class ProjectLocalExperimentStepEntity extends BaseWorkflowStepEntity<WorkflowNodeEntity, ProjectLocalExperimentEntity> {

    @OneToMany(mappedBy = "step", cascade = CascadeType.ALL)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Set<ProjectLocalExperimentStepMessageEntity> messages;

    @OneToMany(mappedBy = "stepOutput", cascade = CascadeType.ALL)
    private Set<ProjectLocalExperimentStepDataEntity> results;

    @OneToMany(mappedBy = "stepInput", cascade = CascadeType.ALL)
    private Set<ProjectLocalExperimentStepDataEntity> inputs;
}
