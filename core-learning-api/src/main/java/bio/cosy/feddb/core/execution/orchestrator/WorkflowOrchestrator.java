package bio.cosy.feddb.core.execution.orchestrator;

import bio.cosy.feddb.core.helper.FileHelper;
import bio.cosy.feddb.core.services.orch.dto.StartWorkflowNodeDTO;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
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

    public void cleanup(Long workflowId) {
        orchestrator.cleanup(workflowId);
    }

    public void cleanup(String containerId, boolean deleteVolumes) {
        orchestrator.cleanup(containerId, deleteVolumes);
        startedContainers.remove(containerId);
    }

    public void cleanupStep(Long stepId) {
        orchestrator.cleanupStep(stepId);
    }

    public void cleanAll() {
        for (String containerId : startedContainers) {
            orchestrator.cleanup(containerId, true);
        }
        startedContainers.clear();
    }

    public List<File> getFiles(Long workflowId, Long workflowNodeId) {
        Log.infof("Downloading output volume: workflowId=%d workflowNodeId=%d", workflowId, workflowNodeId);
        try (Response r = orchestrator.getVolumeClient().downloadFilesIds(workflowId, workflowNodeId)) {
            if (r.getStatus() != 200) {
                throw new IllegalStateException("Output download failed with status " + r.getStatus()
                        + ": " + r.readEntity(String.class));
            }

            Path tmpDir = Files.createTempDirectory(
                    "vol-download-" + workflowId + "-" + workflowNodeId + "-");

            try (InputStream is = r.readEntity(InputStream.class)) {
                return FileHelper.unzip(is, tmpDir);
            }
        } catch (Exception e) {
            Log.errorf(e, "Output download failed: workflowId=%d workflowNodeId=%d", workflowId, workflowNodeId);
            throw new IllegalStateException("Output download failed for workflow " + workflowId
                    + " node workflowNodeId " + workflowNodeId + ": " + e.getMessage(), e);
        }
    }
}
