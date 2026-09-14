package de.unihamburg.daibetes.api.project.experiment.federated.step;

import bio.cosy.feddb.core.api.workflow.base.step.BaseWorkflowStepEntity;
import de.unihamburg.daibetes.api.project.experiment.federated.ProjectFederatedExperimentEntity;
import de.unihamburg.daibetes.api.project.experiment.federated.message.ProjectFederatedExperimentRunMetricsEntity;
import bio.cosy.feddb.core.api.run.RunStatusTypes;
import bio.cosy.feddb.core.base.BaseEntity;
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
@Table(name = "project_federated_experiments_steps")
public class ProjectFederatedExperimentStepEntity extends BaseWorkflowStepEntity<WorkflowNodeEntity, ProjectFederatedExperimentEntity> {

    @OneToMany(mappedBy = "step", cascade = { CascadeType.MERGE, CascadeType.REFRESH })
    private Set<ProjectFederatedExperimentRunMetricsEntity> messages;

   // @OneToOne(mappedBy = "experimentRun", cascade = { CascadeType.MERGE, CascadeType.REFRESH })
   // private ModelSubEntity modelSub;

    // Relay server returns long values (keys/channels) that exceed the default varchar(255).
    @Column(name = "channel_id", columnDefinition = "TEXT")
    private String channel;

    @Column(name = "relay_key", columnDefinition = "TEXT")
    private String relayKey;
}
