package de.unihamburg.daibetes.api.workflow.connection;

import bio.cosy.feddb.core.api.workflow.connection.BaseWorkflowConnectionEntity;
import de.unihamburg.daibetes.api.app.config.input.FederatedAppInputConfigEntity;
import de.unihamburg.daibetes.api.app.config.output.FederatedAppOutputConfigEntity;
import de.unihamburg.daibetes.api.workflow.WorkflowEntity;
import de.unihamburg.daibetes.api.workflow.node.WorkflowNodeEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@Entity
@Table(name = "workflow_connections")
public class WorkflowConnectionEntity extends BaseWorkflowConnectionEntity {

    @ManyToOne(fetch = FetchType.LAZY, cascade = CascadeType.REFRESH)
    @JoinColumn(name = "workflow_id", nullable = false)
    private WorkflowEntity workflow;

    @ManyToOne(fetch = FetchType.LAZY, cascade = CascadeType.REFRESH)
    @JoinColumn(name = "input_node_id", nullable = false)
    private WorkflowNodeEntity inputNode;

    @ManyToOne(fetch = FetchType.LAZY, cascade = CascadeType.REFRESH)
    @JoinColumn(name = "output_node_id")
    private WorkflowNodeEntity outputNode;

    @ManyToOne(fetch = FetchType.LAZY, cascade = CascadeType.REFRESH)
    @JoinColumn(name = "input_config_id", nullable = false)
    private FederatedAppInputConfigEntity inputConfig;

    @ManyToOne(fetch = FetchType.LAZY, cascade = CascadeType.REFRESH)
    @JoinColumn(name = "output_config_id")
    private FederatedAppOutputConfigEntity outputConfig;

    @PrePersist
    @PreUpdate
    private void validateConstraints() {
        if (!isInputConnection() && (outputNode == null || outputConfig == null)) {
            throw new IllegalStateException("outputNode and outputConfig cannot be null when isInputConnection is false");
        }
    }
}
