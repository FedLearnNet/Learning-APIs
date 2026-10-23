package bio.cosy.feddb.core.execution.orchestrator;

import bio.cosy.feddb.core.api.project.ProjectDetailDTO;
import bio.cosy.feddb.core.services.orch.clients.ContainerServiceClient;
import bio.cosy.feddb.core.services.orch.clients.DockerServiceClient;
import bio.cosy.feddb.core.services.orch.clients.VolumeServiceClient;
import bio.cosy.feddb.core.services.orch.clients.WorkflowServiceClient;
import bio.cosy.feddb.core.services.orch.dto.CreateContainerResponseDTO;
import bio.cosy.feddb.core.services.orch.dto.StartAppDTO;
import bio.cosy.feddb.core.services.orch.dto.StartWorkflowNodeDTO;
import bio.cosy.feddb.core.services.orch.dto.VolumeUploadForm;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.resteasy.reactive.ClientWebApplicationException;

import java.io.File;
import java.nio.file.Path;


/**
 * OrchestratorGateway provides a foundation for orchestrating
 * operations related to container management, volume management, and project cleanup tasks.
 * <p>
 * This class centralizes workflow cleanup and
 * serves as a high-level interface for coordinating container-based workloads.
 */
@ApplicationScoped
public class OrchestratorGateway {

    private final ContainerServiceClient containers;
    private final WorkflowServiceClient workflows;
    private final VolumeServiceClient volumes;
    private final DockerServiceClient docker;
    private final int callbackPort;

    @Inject
    public OrchestratorGateway(@RestClient ContainerServiceClient containers,
                               @RestClient WorkflowServiceClient workflows,
                               @RestClient VolumeServiceClient volumes,
                               @RestClient DockerServiceClient docker,
                               @ConfigProperty(name = "quarkus.http.port") int callbackPort) {

        this.containers = containers;
        this.workflows = workflows;
        this.volumes = volumes;
        this.docker = docker;
        this.callbackPort = callbackPort;
    }

    public String startWorkflow(StartWorkflowNodeDTO workflow, String path) throws IllegalArgumentException {

        CreateContainerResponseDTO resp;
        try {
            resp = workflows.startWorkflow(workflow, true, path, callbackPort);
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
        return requireContainerId(resp);
    }

    public String startApplication(
            StartAppDTO request,
            String callbackPath) {

        var response = containers.startContainer(
                request, true, true, callbackPath, callbackPort);

        return requireContainerId(response);
    }

    public void uploadFile(
            Long workflowId,
            Long nodeId,
            Path path) {
        File file = path.toFile();
        uploadFile(workflowId, nodeId, file, file.getName());
    }

    public void uploadFile(
            Long workflowId,
            Long nodeId,
            File file) {
        uploadFile(workflowId, nodeId, file, file.getName());
    }

    public void uploadFile(
            Long workflowId,
            Long nodeId,
            Path path,
            String inputName) {
        File file = path.toFile();
        uploadFile(workflowId, nodeId, file, inputName);
    }

    public void uploadFile(
            Long workflowId,
            Long nodeId,
            File file,
            String inputName) {
        VolumeUploadForm form = new VolumeUploadForm(file, inputName);

        try (Response response =
                     volumes.uploadFilesIds(workflowId, nodeId, form)) {
            requireSuccess(response, "Upload input file");
        }
    }

    /**
     * Cleans up resources associated with a project's workflow.
     * Calls the cleanup endpoint of the OrchAPI service to remove the workflow.
     *
     * @param project The project details containing the workflow to be cleaned up
     */
    public void cleanup(ProjectDetailDTO project) {
        cleanup(project.getId());
    }

    /**
     * Cleans up resources associated with a workflow identified by its project ID.
     * Calls the cleanup endpoint of the OrchAPI service to remove the workflow.
     *
     * @param projectId The ID of the project whose workflow is to be cleaned up
     */
    public void cleanup(Long projectId) {
        if (projectId == null) {
            Log.error("projectId is null, cannot perform cleanup");
            return;
        }
        Log.info("Cleaning up projectId: " + projectId);
        try (Response response = docker.cleanupWorkflow(projectId)) {
            requireSuccess(response, "Clean up application");
        }
    }

    public void cleanup(String containerId, boolean deleteVolumes) {
        if (containerId == null) {
            Log.error("Container ID is null, cannot perform cleanup");
            return;
        }

        Log.info("Cleaning up containerId: " + containerId);

        try (Response response = containers.cleanupWorkflow(containerId, deleteVolumes)) {
            requireSuccess(response, "Clean up application");
        }
    }

    public void cleanupStep(Long stepId) {
        if (stepId == null) {
            Log.error("Step ID is null, cannot perform cleanup");
            return;
        }
        Log.info("Cleaning up stepId: " + stepId);

        try (Response response = workflows.cleanupWorkflowStep(stepId.toString(), true, true)) {
            requireSuccess(response, "Clean up workflow step");
        }
    }

    private String requireContainerId(CreateContainerResponseDTO response) {

        if (response == null || StringUtils.isEmpty(response.getId())) {
            Log.error("orch-api returned no container id for workflow");
            throw new IllegalStateException(
                    "Orchestrator returned no container ID");
        }
        return response.getId();
    }


    private void requireSuccess(Response response, String operation) {
        if (response.getStatusInfo().getFamily() != Response.Status.Family.SUCCESSFUL) {
            throw new IllegalStateException(operation + " failed: HTTP " + response.getStatus());
        }
    }

    private String readResponseBodySafely(ClientWebApplicationException e) {
        try {
            if (e.getResponse() == null) {
                return "<no response>";
            }
            return e.getResponse().readEntity(String.class);
        } catch (Exception readError) {
            return "<unreadable response body: " + readError.getMessage() + ">";
        }
    }

    public VolumeServiceClient getVolumeClient(){
        return this.volumes;
    }
}
