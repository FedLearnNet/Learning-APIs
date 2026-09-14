package bio.cosy.feddb.core.api.socket;

import bio.cosy.feddb.core.api.project.ProjectDetailDTO;
import bio.cosy.feddb.core.api.workflow.WorkflowDTO;
import bio.cosy.feddb.core.base.BaseAuthDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Set;

@EqualsAndHashCode(callSuper = true)
@Data
public class ProjectFederatedExperimentForLocalDTO extends BaseAuthDTO {
    private String globalUniqueId;

    private String name;
    private String description;
    private Boolean modelNeedToBePublic;

    private ProjectDetailDTO projectVersion;
    private WorkflowDTO workflow;
    private String channelId;

    private Set<String> roles;
}
