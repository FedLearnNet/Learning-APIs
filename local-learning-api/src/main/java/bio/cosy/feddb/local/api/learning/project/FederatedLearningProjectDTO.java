package bio.cosy.feddb.local.api.learning.project;

import bio.cosy.feddb.core.api.project.ProjectDetailDTO;
import bio.cosy.feddb.local.api.learning.project.run.FederatedLearningExperimentDTO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FederatedLearningProjectDTO {

    private ProjectDetailDTO project;
    private FederatedLearningExperimentDTO experiment;
    private Long workflowId;
}
