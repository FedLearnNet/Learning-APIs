package bio.cosy.feddb.core.services.orch.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class StartWorkflowNodeDTO {
    @NotNull(message = "workflowNodeId cannot be null")
    private Long workflowNodeId;

    @NotBlank(message = "appImage cannot be blank")
    private String appImage;

    @NotNull(message = "workflowId cannot be null")
    private Long workflowId;

    private List<WorkflowNodeInputActionDTO> inputs = new ArrayList<>();

    private Boolean isFistNode = false;
    private Boolean isLastNode = false;

    //Network settings
    private Boolean needsInternetAccess = false;
    private Boolean needsHostAccess = false;
    private Boolean needsFederatedLearningAccess = false;

    private Boolean enableRemoteResultSaving = false;

    private List<String> environments = new ArrayList<>();

    public void upsertEnv(String key, String value) {
        if (environments == null) {
            environments = new ArrayList<>();
        } else {
            environments = new ArrayList<>(environments);
        }

        final String prefix = key + "=";
        boolean replaced = false;
        for (int i = 0; i < environments.size(); i++) {
            String entry = environments.get(i);
            if (entry != null && entry.startsWith(prefix)) {
                environments.set(i, prefix + value);
                replaced = true;
                break;
            }
        }
        if (!replaced) {
            environments.add(prefix + value);
        }
    }

}
