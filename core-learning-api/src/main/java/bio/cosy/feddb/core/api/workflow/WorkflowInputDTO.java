package bio.cosy.feddb.core.api.workflow;

import bio.cosy.feddb.core.api.app.config.ToolInputConfigDTO;
import bio.cosy.feddb.core.api.workflow.node.WorkflowPositionDTO;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class WorkflowInputDTO extends ToolInputConfigDTO {
    @NotBlank(message = "NodeId needs to bet set")
    private String nodeId;
    private WorkflowPositionDTO position;
}
