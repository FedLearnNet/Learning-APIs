package de.unihamburg.daibetes.execution.experiment;

import bio.cosy.feddb.core.execution.experiment.BaseExecutionExperimentEntity;
import de.unihamburg.daibetes.api.app.version.FederatedAppVersionEntity;
import bio.cosy.feddb.core.api.project.ProjectDetailDTO;
import bio.cosy.feddb.core.api.project.ProjectStatus;
import de.unihamburg.daibetes.api.project.ProjectEntity;
import de.unihamburg.daibetes.api.workflow.WorkflowEntity;
import de.unihamburg.daibetes.execution.run.ExecutionRunEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.ArrayList;
import java.util.List;

/**
 * An experiment on the platform: a developer's experiment with an app while building it, or the training of a
 * project's workflow, on the platform or across clinics. Its runs point to it.
 */
@Setter
@Getter
@Entity
@Table(name = "execution_experiment")
public class ExecutionExperimentEntity extends BaseExecutionExperimentEntity {

    // User who set it up.
    @Column(name = "keycloak_id")
    private String keycloakId;

    // A test of a project's workflow on dummy data instead of a training of it.
    @Column(name = "is_test", nullable = false)
    private boolean test;

    // The app a developer experiments with while building it.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "app_version_id")
    private FederatedAppVersionEntity federatedAppVersion;

    // The project whose workflow it trains.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id")
    private ProjectEntity project;

    @OneToMany(mappedBy = "experiment")
    @OrderBy("id ASC")
    private List<ExecutionRunEntity> runs = new ArrayList<>();

    // For a training across clinics: how the clinics know it, and how many accepted it before it starts.
    @Column(name = "global_unique_id", unique = true)
    private String globalUniqueId;

    @Enumerated(EnumType.STRING)
    private ProjectStatus status;

    // The project as the clinics accepted it.
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "project_version", columnDefinition = "jsonb")
    private ProjectDetailDTO projectVersion;

    // A copy of the project's workflow, so editing the project leaves the training as it is.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workflow_id")
    private WorkflowEntity workflow;

    @Column(name = "acceptance_count")
    private Long acceptanceCount;

    @Column(name = "acceptance_clinic_count")
    private Long acceptanceClinicCount;

    @Column(name = "relay_server_address")
    private String relayServerAddress;

    @Column(name = "channel_id", length = 64)
    private String channelId;

    @Column(name = "model_can_be_public")
    private Boolean modelCanBePublic;

    @Column(name = "model_need_to_be_public")
    private Boolean modelNeedToBePublic;
}
