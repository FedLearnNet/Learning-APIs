package bio.cosy.feddb.core.api.workflow.base.experiment;

import bio.cosy.feddb.core.api.project.ProjectStatus;
import bio.cosy.feddb.core.base.BaseDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;

@EqualsAndHashCode(callSuper = true)
@Data
public class BaseWorkflowExperimentDTO extends BaseDTO {

    private Date finishedAt;
    private Date startedAt;

    private ProjectStatus experimentStatus;
    private Long currentWorkflowNodeId;
}
