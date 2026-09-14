package bio.cosy.feddb.local.api.workflow.connection;

import bio.cosy.feddb.core.api.workflow.connection.BaseWorkflowConnectionEntity;
import bio.cosy.feddb.local.api.workflow.WorkflowEntity;
import bio.cosy.feddb.local.api.workflow.node.WorkflowNodeEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@Entity
@Table(name = "workflow_connections",
        indexes = {
                @Index(name = "idx_workflow_connections_workflow_id", columnList = "workflow_id"),
                @Index(name = "idx_workflow_connections_input_node_id", columnList = "input_node_id"),
                @Index(name = "idx_workflow_connections_output_node_id", columnList = "output_node_id")
        })
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

    @Column(name = "input_file_name")
    private String inputFileName;

    @Column(name = "output_file_name")
    private String outputFileName;

    @Column(name = "input_config_name", nullable = false)
    private String inputConfigName;

    @Column(name = "output_config_name")
    private String outputConfigName;

    @PrePersist
    @PreUpdate
    private void validateConstraints() {
        if (!isInputConnection() && (outputNode == null || outputConfigName == null)) {
            throw new IllegalStateException("outputNode and outputConfigName cannot be null when isInputConnection is false");
        }
    }
}
