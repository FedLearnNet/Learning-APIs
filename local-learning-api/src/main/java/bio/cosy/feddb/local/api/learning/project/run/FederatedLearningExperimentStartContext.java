package bio.cosy.feddb.local.api.learning.project.run;

import bio.cosy.feddb.core.api.workflow.node.WorkflowNodeDetailDTO;
import bio.cosy.feddb.core.services.orch.dto.StartWorkflowNodeDTO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class FederatedLearningExperimentStartContext {
    private Long stepId;
    private WorkflowNodeDetailDTO node;
    private StartWorkflowNodeDTO startWorkflowDTO;
    private boolean oldFCVersion;
    private LinkedHashMap<String, Object> nodeHyperParams;
}
