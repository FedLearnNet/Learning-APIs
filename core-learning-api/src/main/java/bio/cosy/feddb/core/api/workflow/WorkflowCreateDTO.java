package bio.cosy.feddb.core.api.workflow;

import jakarta.annotation.Nullable;
import lombok.Data;

@Data
public class WorkflowCreateDTO {
    @Nullable
    private Long projectId;
}
