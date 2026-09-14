package de.unihamburg.daibetes.api.project.experiment.federated;

import bio.cosy.feddb.core.api.project.ProjectDetailDTO;
import bio.cosy.feddb.core.api.workflow.base.experiment.BaseWorkflowExperimentDTO;
import de.unihamburg.daibetes.api.runs.experiment.ExperimentDiagramConfigDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
public class ProjectFederatedExperimentDTO extends BaseWorkflowExperimentDTO {

    private String globalUniqueId;

    private String name;
    private String description;

    private Long acceptanceCount;
    private Long acceptanceClinicCount;

    private ProjectDetailDTO projectVersion;
    private Long workflowId;

    private Boolean modelCanBePublic;
    private Boolean modelNeedToBePublic;

    private List<ExperimentDiagramConfigDTO> diagramConfigs;

    private String relayServerAddress;

    private Boolean clinicIsCoordinator;
    private Long coordinatorId;

    private Long projectId;
    private String channelId;

    //UUID generated in the business layer, not shown to user;
    //can be used if project support hyperparam tuning or multiple experiment runs grouping
    //For now not used
    private String groupId;
}
