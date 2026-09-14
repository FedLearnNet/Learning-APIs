package bio.cosy.feddb.core.services.orch;

import bio.cosy.feddb.core.api.project.ProjectDetailDTO;
import bio.cosy.feddb.core.base.BaseOrchestrator;
import bio.cosy.feddb.core.services.orch.dto.ConfigYMLDTO;
import bio.cosy.feddb.core.services.orch.dto.CreateContainerResponseDTO;
import bio.cosy.feddb.core.services.orch.dto.StartWorkflowNodeDTO;
import bio.cosy.feddb.core.services.orch.dto.VolumeUploadForm;
import io.quarkus.logging.Log;
import jakarta.ws.rs.core.Response;
import org.jboss.resteasy.reactive.ClientWebApplicationException;

import java.io.File;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;


public abstract class WorkflowOrchestrator extends BaseOrchestrator {
    private final Set<String> startedContainers = new HashSet<>();

    public String executeWorkflow(StartWorkflowNodeDTO workflow) throws IllegalArgumentException {
        return executeWorkflow(workflow, "learning/run/");
    }

    public String executeWorkflow(StartWorkflowNodeDTO workflow, String path) throws IllegalArgumentException {

        CreateContainerResponseDTO resp;
        try {
            resp = getWorkflowClient().startWorkflow(workflow, true, path, serverPort);
        } catch (ClientWebApplicationException e) {
            // orch-api responded with an error status: surface the response body it sent back.
            int status = e.getResponse() != null ? e.getResponse().getStatus() : -1;
            String body = readResponseBodySafely(e);
            Log.errorf(e, "orch-api rejected startWorkflow for workflow %d node %d (status %d): %s",
                    workflow.getWorkflowId(), workflow.getWorkflowNodeId(), status, body);
            throw new IllegalStateException("Failed to start container (status " + status + "): " + body, e);
        } catch (Exception e) {
            Log.errorf(e, "Failed to reach orch-api to start workflow %d node %d: %s",
                    workflow.getWorkflowId(), workflow.getWorkflowNodeId(), e.getMessage());
            throw new IllegalStateException("Failed to start container: " + e.getMessage(), e);
        }
        String containerId = resp.getId();
        if (containerId == null) {
            Log.errorf("orch-api returned no container id for workflow %d node %d",
                    workflow.getWorkflowId(), workflow.getWorkflowNodeId());
            throw new IllegalStateException("Failed to start container for step " + workflow.getWorkflowNodeId());
        }
        startedContainers.add(containerId);
        return containerId;
    }

    private static String readResponseBodySafely(ClientWebApplicationException e) {
        try {
            if (e.getResponse() == null) {
                return "<no response>";
            }
            return e.getResponse().readEntity(String.class);
        } catch (Exception readError) {
            return "<unreadable response body: " + readError.getMessage() + ">";
        }
    }

    public void uploadFilesToVolume(Long workflowId, Long workflowNodeId, Path filePath, String name) {
        File file = filePath.toFile();
        uploadFilesToVolume(workflowId, workflowNodeId, file, name);
    }

    public void uploadFilesToVolume(Long workflowId, Long workflowNodeId, File file, String name) {
        VolumeUploadForm form = new VolumeUploadForm(file, name);
        try (Response response = getVolumeClient().uploadFilesIds(workflowId, workflowNodeId, form)) {
            if (response.getStatus() != Response.Status.OK.getStatusCode()) {
                Log.error("Failed to upload files to volume: " + response.readEntity(String.class));
            }
        } catch (Exception e) {
            Log.error("Error uploading files to volume: " + e.getMessage(), e);
        }
    }

    public void uploadConfigToVolume(ProjectDetailDTO project, Long workflowNodeId, String config) {
        try {
            uploadConfigToVolume(project.getId(), workflowNodeId, config);
        } catch (Exception e) {
            String statusCode = e.getMessage() != null ? e.getMessage().split(" ")[0] : "Unknown";
            Log.error("Failed to upload config (Status code: " + statusCode + "): " + e.getMessage(), e);
            throw new IllegalStateException("Failed to upload config: " + e.getMessage(), e);
        }
    }

    public void uploadConfigToVolume(Long workflowId, Long workflowNodeId, String config) {
        ConfigYMLDTO configDTO = new ConfigYMLDTO();
        configDTO.setContent(config);
        try (Response response = getVolumeClient().uploadFilesIdsConfig(workflowId, workflowNodeId, configDTO)) {
            if (response.getStatus() != Response.Status.OK.getStatusCode()) {
                Log.error("Failed to upload files to volume: " + response.readEntity(String.class));
            }
        } catch (Exception e) {
            Log.error("Error uploading files to volume: " + e.getMessage(), e);
        }
    }


    public void cleanAll() {
        for (String containerId : startedContainers) {

            try (Response response = getContainerClient().cleanupWorkflow(containerId, true)) {
                Log.info("Cleaned up container " + containerId);
            } catch (Exception e) {
                // Log the error but continue with cleanup
                Log.error("Failed to stop container " + containerId + ": " + e.getMessage());

            }

        }
        startedContainers.clear();
    }
}
