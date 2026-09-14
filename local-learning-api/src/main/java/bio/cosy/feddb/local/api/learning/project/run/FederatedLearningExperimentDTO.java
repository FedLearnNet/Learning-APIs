package bio.cosy.feddb.local.api.learning.project.run;

import bio.cosy.feddb.core.api.workflow.base.experiment.BaseWorkflowExperimentDTO;
import bio.cosy.feddb.local.api.learning.project.run.step.FederatedLearningExperimentStepDTO;
import bio.cosy.feddb.local.api.learning.project.run.step.FederatedLearningExperimentStepDetailDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
public class FederatedLearningExperimentDTO extends BaseWorkflowExperimentDTO {

    private String uniqueRandomClinicId;

    private List<FederatedLearningExperimentStepDTO> steps;

    //Project which is linked to a workflow
    private Long projectId;

    //workflow
    private Long workflowId;
    private Long workflowVersion;

    //UUID generated in the business layer, not shown to user;
    //can be used if project support hyperparam tuning or multiple experiment runs grouping
    //For now not used
    private String groupId;
}
