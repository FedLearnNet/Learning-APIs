package bio.cosy.feddb.core.api.workflow.base.experiment;

import bio.cosy.feddb.core.services.orch.dto.StartWorkflowNodeDTO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BaseWorkflowExperimentInitContext {
    private StartWorkflowNodeDTO startDTO;
    private boolean isOldFCVersion;
    private Long stepId;
    private LinkedHashMap<String, Object> nodeHyperParams;

    public boolean hyperparamsAreSet() {
        return nodeHyperParams != null && !nodeHyperParams.isEmpty();
    }
}
