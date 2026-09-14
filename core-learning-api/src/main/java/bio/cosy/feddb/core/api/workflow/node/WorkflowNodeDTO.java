package bio.cosy.feddb.core.api.workflow.node;

import bio.cosy.feddb.core.base.BaseDTO;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.LinkedHashMap;

@EqualsAndHashCode(callSuper = true)
@Data
public class WorkflowNodeDTO extends BaseDTO {

    @NotBlank(message = "NodeId needs to bet set")
    private String nodeId;

    private Long workflowId;
    private Long federatedAppId;
    private Long federatedAppVersionId;

    private Long modelSubId;

    private String appVersion;
    private String imageName;

    private WorkflowPositionDTO position;

    private LinkedHashMap<String, Object> hyperParams;

    private Boolean oldFCVersion;
    private Boolean supportsFederatedLearning = false;
    private Boolean isTrainable = false;


    private boolean hasChildren = false;
    private boolean hasParent = false;

    //will be calculated on create/update
    private Integer executionOrder;

    public boolean isOldFCVersion() {
        return oldFCVersion != null && oldFCVersion;
    }
}
