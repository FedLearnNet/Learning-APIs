package de.unihamburg.daibetes.api.project;

import bio.cosy.feddb.core.api.project.PatientDataExportConfigDTO;
import bio.cosy.feddb.core.base.BaseEntity;
import de.unihamburg.daibetes.api.file.FileEntity;
import de.unihamburg.daibetes.api.project.experiment.federated.ProjectFederatedExperimentEntity;
import de.unihamburg.daibetes.api.project.membership.ProjectMembershipEntity;
import de.unihamburg.daibetes.api.query.QueryEntity;
import de.unihamburg.daibetes.api.workflow.WorkflowEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.hibernate.type.SqlTypes;

import java.util.Date;
import java.util.Set;

@Entity(name = "ProjectEntity")
@Table(name = "projects")
@Setter
@Getter
public class ProjectEntity extends BaseEntity {

    @Column(name = "verified_on")
    private Date verifiedOn;

    @ColumnDefault("0")
    private int certificationLevel = 0;

    private String name;

    @Column(length = 1000)
    private String description;

    @ColumnDefault("false")
    private boolean isAudited = false;

    @Column(name = "export_config", columnDefinition = "json")
    @JdbcTypeCode(SqlTypes.JSON)
    private PatientDataExportConfigDTO exportConfig;

    @Column(name = "coordinator_has_data", columnDefinition = "boolean default true")
    private Boolean coordinatorHasData;

    @Column(name = "platform_is_coordinator", columnDefinition = "boolean default false")
    private Boolean platformIsCoordinator;


    @ManyToOne(cascade = CascadeType.REFRESH)
    @JoinColumn(name = "query_id")
    private QueryEntity query;

    @ManyToOne(cascade = CascadeType.REFRESH)
    @JoinColumn(name = "workflow_id")
    private WorkflowEntity workflow;

    @ManyToOne(cascade = CascadeType.REFRESH)
    @JoinColumn(name = "file_id")
    @OnDelete(action = OnDeleteAction.CASCADE)
    private FileEntity file;

    @OneToMany(mappedBy = "project", cascade = CascadeType.ALL)
    private Set<ProjectMembershipEntity> memberships;

    @OneToMany(mappedBy = "project", cascade = CascadeType.ALL)
    private Set<ProjectFederatedExperimentEntity> experiments;

}
