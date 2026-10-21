package bio.cosy.feddb.core.execution.orchestrator;

import bio.cosy.feddb.core.services.orch.dto.StartWorkflowNodeDTO;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.io.File;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;


@ApplicationScoped
public class WorkflowOrchestrator {
    @Inject
    OrchestratorGateway orchestrator;

    private final Set<String> startedContainers = new HashSet<>();

    public String executeWorkflow(StartWorkflowNodeDTO workflow, String path) throws IllegalArgumentException {
        String containerId = orchestrator.startWorkflow(workflow, path);
        startedContainers.add(containerId);
        return containerId;
    }

    public void uploadFilesToVolume(Long workflowId, Long workflowNodeId, Path filePath, String name) {
        File file = filePath.toFile();
        uploadFilesToVolume(workflowId, workflowNodeId, file, name);
    }

    public void uploadFilesToVolume(Long workflowId, Long workflowNodeId, File file, String name) {
        orchestrator.uploadFile(workflowId, workflowNodeId, file, name);
    }

    public void cleanAll() {
        for (String containerId : startedContainers) {
            orchestrator.cleanup(containerId, true);
        }
        startedContainers.clear();
    }
}
