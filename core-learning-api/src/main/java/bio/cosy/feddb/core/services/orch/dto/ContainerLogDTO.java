package bio.cosy.feddb.core.services.orch.dto;

import bio.cosy.feddb.core.base.BaseDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;


@Data
@EqualsAndHashCode(callSuper = true)
public class ContainerLogDTO extends BaseDTO {
    private Long appId;

    private String appImage;

    private Long workflowId;

    private Long workflowStep;

    private Long workflowMaxSteps;

    private String containerName;

    private String containerId;

    String log;
}
