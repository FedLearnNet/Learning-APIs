package bio.cosy.feddb.core.api.workflow;

import bio.cosy.feddb.core.api.app.PublishStatus;
import bio.cosy.feddb.core.api.workflow.connection.WorkflowConnectionDTO;
import bio.cosy.feddb.core.api.workflow.node.WorkflowNodeDetailDTO;
import bio.cosy.feddb.core.base.BaseAuthDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;
import java.util.Optional;

@EqualsAndHashCode(callSuper = true)
@Data
public class WorkflowDTO extends BaseAuthDTO {
    private List<WorkflowNodeDetailDTO> nodes = List.of();
    private List<WorkflowConnectionDTO> connections = List.of();
    private List<WorkflowInputDTO> inputs = List.of();

    private String name;
    private String description;
    private PublishStatus publishStatus;

    public Optional<WorkflowNodeDetailDTO> nodeOf(String nodeId) {
        return nodes.stream().filter(node -> nodeId.equals(node.getNodeId())).findFirst();
    }

    /** Whether one of the workflow's inputs feeds the node. */
    public boolean feedsInputInto(String nodeId) {
        return connections.stream().anyMatch(connection -> connection.isInputConnection() && nodeId.equals(connection.getInputNodeId()));
    }
}
