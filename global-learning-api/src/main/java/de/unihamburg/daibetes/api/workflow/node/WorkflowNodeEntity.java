package de.unihamburg.daibetes.api.workflow.node;

import bio.cosy.feddb.core.api.workflow.node.BaseWorkflowNodeEntity;
import de.unihamburg.daibetes.api.app.version.FederatedAppVersionEntity;
import de.unihamburg.daibetes.api.model.sub.ModelSubEntity;
import de.unihamburg.daibetes.api.workflow.WorkflowEntity;
import de.unihamburg.daibetes.api.workflow.connection.WorkflowConnectionEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.util.HashSet;
import java.util.Set;

@Setter
@Getter
@Entity
@Table(name = "workflow_node")
public class WorkflowNodeEntity extends BaseWorkflowNodeEntity {

    @ManyToOne(cascade = CascadeType.REFRESH)
    @JoinColumn(name = "workflow_id", nullable = false)
    private WorkflowEntity workflow;

    @ManyToOne(cascade = CascadeType.REFRESH)
    @JoinColumn(name = "app_version_id", nullable = true)
    private FederatedAppVersionEntity federatedAppVersion;

    @ManyToOne
    @JoinColumn(name = "sub_model_id", nullable = true)
    private ModelSubEntity subModel;

    @OneToMany(mappedBy = "inputNode", cascade = CascadeType.ALL)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Set<WorkflowConnectionEntity> inComingConnections = new HashSet<>();

    @OneToMany(mappedBy = "outputNode", cascade = CascadeType.ALL)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Set<WorkflowConnectionEntity> outComingConnections = new HashSet<>();
}
