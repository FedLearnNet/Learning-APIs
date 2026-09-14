package de.unihamburg.daibetes.api.workflow;

import bio.cosy.feddb.core.api.app.PublishStatus;
import bio.cosy.feddb.core.api.project.PatientDataExportConfigDTO;
import bio.cosy.feddb.core.api.workflow.WorkflowInputDTO;
import bio.cosy.feddb.core.base.BaseAuthDTO;
import bio.cosy.feddb.core.base.BaseAuthEntity;
import bio.cosy.feddb.core.base.BaseEntity;
import de.unihamburg.daibetes.api.workflow.connection.WorkflowConnectionEntity;
import de.unihamburg.daibetes.api.workflow.node.WorkflowNodeEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.hibernate.type.SqlTypes;

import java.util.List;
import java.util.Set;

@Setter
@Getter
@Entity
@Table(name = "workflow")
public class WorkflowEntity extends BaseAuthEntity {

    @Column(name = "publish_status", length = 50)
    @Enumerated(EnumType.STRING)
    private PublishStatus publishStatus = PublishStatus.UNPUBLISHED;

    private String name;

    @Column(length = 2000)
    private String description;

    @Column(name = "export_config", columnDefinition = "json")
    @JdbcTypeCode(SqlTypes.JSON)
    private List<WorkflowInputDTO> inputs = List.of();

    @OneToMany(mappedBy = "workflow", cascade = CascadeType.ALL)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Set<WorkflowNodeEntity> nodes;

    @OneToMany(mappedBy = "workflow", cascade = CascadeType.ALL)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Set<WorkflowConnectionEntity> connections;
}
