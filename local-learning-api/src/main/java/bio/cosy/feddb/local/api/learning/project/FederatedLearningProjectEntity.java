package bio.cosy.feddb.local.api.learning.project;

import bio.cosy.feddb.core.api.project.PatientDataExportConfigDTO;
import bio.cosy.feddb.core.base.BaseEntity;
import bio.cosy.feddb.local.api.learning.project.run.FederatedLearningExperimentEntity;
import bio.cosy.feddb.local.api.learning.request.FederatedLearningRequestEntity;
import bio.cosy.feddb.local.api.query.QueryEntity;
import bio.cosy.feddb.local.api.workflow.WorkflowEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.envers.Audited;
import org.hibernate.type.SqlTypes;

import java.util.Date;

@Entity
@Table(name = "federated_learning_project",
        indexes = {
                @Index(name = "idx_federated_learning_project_query_id", columnList = "query_id"),
                @Index(name = "idx_federated_learning_project_workflow_id", columnList = "workflow_id"),
                @Index(name = "idx_federated_learning_project_request_id", columnList = "request_id")
        })
@Getter
@Setter
public class FederatedLearningProjectEntity extends BaseEntity {

    @Column(name = "verified_on")
    private Date verifiedOn;

    @ColumnDefault("0")
    private int certificationLevel = 0;

    private String name;

    @Column(length = 1000)
    private String description;

    @Column(name = "export_config", columnDefinition = "json")
    @JdbcTypeCode(SqlTypes.JSON)
    private PatientDataExportConfigDTO exportConfig;

    @Column(name = "coordinator_has_data", columnDefinition = "boolean default true")
    private Boolean coordinatorHasData;

    @Column(name = "platform_is_coordinator", columnDefinition = "boolean default false")
    private Boolean platformIsCoordinator;

    @Column(name = "is_coordinator", columnDefinition = "boolean default false")
    private Boolean isCoordinator;


    @ManyToOne(cascade = CascadeType.REFRESH)
    @JoinColumn(name = "query_id")
    private QueryEntity query;

    @ManyToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "workflow_id")
    private WorkflowEntity workflow;

    @OneToOne
    @JoinColumn(name = "request_id", nullable = false)
    private FederatedLearningRequestEntity request;

    @OneToOne(mappedBy = "project")
    private FederatedLearningExperimentEntity experiment;
}
