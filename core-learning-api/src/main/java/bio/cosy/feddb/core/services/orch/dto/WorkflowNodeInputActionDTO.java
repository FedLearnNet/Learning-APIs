package bio.cosy.feddb.core.services.orch.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class WorkflowNodeInputActionDTO {
    @NotBlank(message = "originalFileName cannot be blank")
    private String originalFileName;
    @NotBlank(message = "newFileName cannot be blank")
    private String newFileName;

    @NotNull(message = "inputNodeId cannot be null")
    private Long inputNodeId;
    private boolean isLastUsage = false;
}
