package bio.cosy.feddb.core.api.workflow.connection;

import bio.cosy.feddb.core.base.BaseDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class WorkflowConnectionDTO extends BaseDTO {

    private String inputId;
    private String outputId;

    private boolean isInputConnection;

    private String inputNodeId;
    private String outputNodeId;

    private String inputFileName;
    private String outputFileName;

    private String inputConfigName;
    private String outputConfigName;

    private Long workflowId;
}
