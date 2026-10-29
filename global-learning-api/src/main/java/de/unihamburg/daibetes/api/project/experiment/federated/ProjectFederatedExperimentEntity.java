package de.unihamburg.daibetes.api.project.experiment.federated;

import bio.cosy.feddb.core.api.workflow.base.experiment.BaseWorkflowExperimentEntity;
import de.unihamburg.daibetes.api.project.ProjectEntity;
import de.unihamburg.daibetes.api.project.experiment.federated.participants.ProjectFederatedExperimentParticipantEntity;
import de.unihamburg.daibetes.api.project.experiment.federated.step.ProjectFederatedExperimentStepEntity;
import de.unihamburg.daibetes.api.project.experiment.local.step.ProjectLocalExperimentStepEntity;
import de.unihamburg.daibetes.api.workflow.WorkflowEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.Set;

@Setter
@Getter
@Entity
@Table(name = "project_federated_experiments")
public class ProjectFederatedExperimentEntity extends BaseWorkflowExperimentEntity<ProjectFederatedExperimentStepEntity> {

    @Column(name = "global_unique_id", unique = true, nullable = false)
    private String globalUniqueId;

    private String name;
    private String description;

    @Column(name = "project_version", columnDefinition = "TEXT")
    private String projectVersion;

    @Column(name = "acceptance_count")
    private Long acceptanceCount;

    @Column(name = "acceptance_clinic_count")
    private Long acceptanceClinicCount;

    @Column(name = "relay_server_address")
    private String relayServerAddress = "";

    @Column(name = "clinic_is_coordinator", columnDefinition = "boolean default false")
    private boolean clinicIsCoordinator;

    @OneToOne(cascade = {CascadeType.MERGE, CascadeType.REFRESH})
    @JoinColumn(name = "coordinator_id", nullable = true)
    private ProjectFederatedExperimentParticipantEntity coordinator;

    @Column(columnDefinition = "TEXT", name = "diagram_config")
    private String diagramConfig;

    @Column(length = 64, name = "channel_id")
    private String channelId;

    @Column(name = "model_can_be_public", columnDefinition = "boolean default true")
    private Boolean modelCanBePublic;

    @Column(name = "model_need_to_be_public", columnDefinition = "boolean default false")
    private Boolean modelNeedToBePublic;

    @ManyToOne(cascade = {CascadeType.MERGE, CascadeType.REFRESH})
    @JoinColumn(name = "project_id", nullable = false)
    private ProjectEntity project;

    @ManyToOne(cascade = CascadeType.REFRESH)
    @JoinColumn(name = "workflow_id")
    private WorkflowEntity workflow;

    @OneToMany(mappedBy = "experiment", cascade = CascadeType.ALL)
    private Set<ProjectFederatedExperimentParticipantEntity> participants;

    @OneToMany(mappedBy = "experiment", cascade = CascadeType.ALL)
    private Set<ProjectFederatedExperimentStepEntity> steps;
}
