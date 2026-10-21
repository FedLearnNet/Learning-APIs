package bio.cosy.feddb.core.api.workflow.base.step;

import bio.cosy.feddb.core.execution.run.dto.RunMetaDTO;
import bio.cosy.feddb.core.execution.run.types.RunStatusTypes;
import bio.cosy.feddb.core.base.BaseDTO;
import jakarta.persistence.Column;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hibernate.annotations.OptimisticLock;

@EqualsAndHashCode(callSuper = true)
@Data
public class BaseWorkflowStepDTO extends BaseDTO {

    private RunStatusTypes stepStatus;
    private String lastError;

    // Run metadata (timings today). Set on finish from FinishRunDTO.meta.
    private RunMetaDTO meta;

    @OptimisticLock(excluded = true)
    @Column(name = "progress")
    private Float progress;

    private String containerId;

    private Long workflowNodeId;
    private Long experimentId;
}
