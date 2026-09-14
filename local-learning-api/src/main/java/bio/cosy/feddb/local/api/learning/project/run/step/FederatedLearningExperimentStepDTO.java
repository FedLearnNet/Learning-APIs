package bio.cosy.feddb.local.api.learning.project.run.step;

import bio.cosy.feddb.core.api.socket.FederatedLearningRelayInfoDTO;
import bio.cosy.feddb.core.api.workflow.base.step.BaseWorkflowStepDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class FederatedLearningExperimentStepDTO extends BaseWorkflowStepDTO {
    private String globalRequestId;

    private FederatedLearningRelayInfoDTO relayInfo;
}
