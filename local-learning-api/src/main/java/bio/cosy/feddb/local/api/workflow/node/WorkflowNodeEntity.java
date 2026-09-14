package bio.cosy.feddb.local.api.workflow.node;

import bio.cosy.feddb.core.api.workflow.node.BaseWorkflowNodeEntity;
import bio.cosy.feddb.local.api.workflow.WorkflowEntity;
import bio.cosy.feddb.local.api.workflow.connection.WorkflowConnectionEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

import java.util.HashSet;
import java.util.Set;

@Setter
@Getter
@Entity
@Table(name = "workflow_node",
        indexes = {
                @Index(name = "idx_workflow_node_workflow_id", columnList = "workflow_id")
        })
public class WorkflowNodeEntity extends BaseWorkflowNodeEntity {

    @ManyToOne(cascade = CascadeType.REFRESH)
    @JoinColumn(name = "workflow_id", nullable = false)
    private WorkflowEntity workflow;

    @Column(name = "global_app_id")
    private Long federatedAppId;
    @Column(name = "global_app_version_id")
    private Long federatedAppVersionId;

    @Column(name = "global_model_sub_id")
    private Long modelSubId;

    @Column(name = "old_fc_version", nullable = true)
    private Boolean oldFCVersion = false;

    @ColumnDefault("false")
    @Column(name = "supports_federated_learning")
    private Boolean supportsFederatedLearning = false;

    @ColumnDefault("false")
    @Column(name = "is_trainable")
    private Boolean isTrainable = false;

    private String imageName;

    @OneToMany(mappedBy = "inputNode", cascade = CascadeType.ALL)
    private Set<WorkflowConnectionEntity> inComingConnections = new HashSet<>();

    @OneToMany(mappedBy = "outputNode", cascade = CascadeType.ALL)
    private Set<WorkflowConnectionEntity> outComingConnections = new HashSet<>();

    @Override
    public boolean isOldFCVersion() {
        return oldFCVersion != null && oldFCVersion;
    }

    /**
     * Must be overridden: the base class hardcodes {@code return false}, and Lombok generates
     * {@code getSupportsFederatedLearning()} for the Boolean field - not this method. Without the
     * override, handleStartLearning never registers the learning on the controller
     * (POST /start-learning), and every app data call fails with 404 "no such run key".
     */
    @Override
    public boolean isSupportsFederatedLearning() {
        return Boolean.TRUE.equals(supportsFederatedLearning);
    }
}
