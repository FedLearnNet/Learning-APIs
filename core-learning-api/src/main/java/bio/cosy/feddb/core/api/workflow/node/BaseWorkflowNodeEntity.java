package bio.cosy.feddb.core.api.workflow.node;

import bio.cosy.feddb.core.base.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@MappedSuperclass
public class BaseWorkflowNodeEntity extends BaseEntity {

    @Column(name = "node_id")
    private String nodeId;

    @Column(columnDefinition = "TEXT")
    private String hyperParams;

    @Column(columnDefinition = "TEXT")
    private String position;

    @Column(name = "execution_order")
    private Integer executionOrder;

    //by default only v2
    public boolean isOldFCVersion() {
        return false;
    }

    //by default false
    public boolean isSupportsFederatedLearning() {
        return false;
    }
}
