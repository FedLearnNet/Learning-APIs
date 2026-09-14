package bio.cosy.feddb.core.base;

import bio.cosy.feddb.core.api.project.ProjectDetailDTO;
import bio.cosy.feddb.core.services.orch.clients.ContainerServiceClient;
import bio.cosy.feddb.core.services.orch.clients.DockerServiceClient;
import bio.cosy.feddb.core.services.orch.clients.VolumeServiceClient;
import bio.cosy.feddb.core.services.orch.clients.WorkflowServiceClient;
import bio.cosy.feddb.core.services.orch.dto.ContainerDTO;
import bio.cosy.feddb.core.services.orch.dto.ContainerRunDTO;
import io.quarkus.logging.Log;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.List;
import java.util.Optional;


/**
 * BaseOrchestrator is an abstract class that provides a foundation for orchestrating
 * operations related to container management, volume management, and project cleanup tasks.
 * It defines abstract methods to be implemented by subclasses for integrating with specific
 * client services responsible for handling these operations.
 * <p>
 * Subclasses must implement the abstract methods to provide instances of services such as
 * containers, volumes, and Docker management. This class centralizes workflow cleanup and
 * serves as a high-level interface for coordinating container-based workloads.
 */
public abstract class BaseOrchestrator {

    @ConfigProperty(name = "quarkus.http.port")
    protected int serverPort;

    protected abstract ContainerServiceClient getContainerClient();

    protected abstract WorkflowServiceClient getWorkflowClient();

    protected abstract VolumeServiceClient getVolumeClient();

    protected abstract DockerServiceClient getDockerClient();

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
        getDockerClient().cleanupWorkflow(projectId);
    }

    public void cleanup(String containerId, boolean cleanup) {
        if (containerId == null) {
            Log.error("Container ID is null, cannot perform cleanup");
            return;
        }
        getWorkflowClient().cleanupWorkflow(containerId, cleanup, true);
    }

    public void cleanupWorkflowNode(Long stepId) {
        getWorkflowClient().cleanupWorkflowStep(stepId.toString(), true, true);
    }


    /**
     * Retrieves details of a container run by its ID.
     *
     * @param runId The unique identifier of the container run
     * @return ContainerRunDTO containing details of the specified container run
     */
    public ContainerRunDTO getRunContainer(Long runId) {
        return getContainerClient().getRun(runId);
    }

    /**
     * Retrieves details of a container by its ID.
     *
     * @param containerId The unique identifier of the container
     * @return ContainerDTO containing details of the specified container
     */
    public ContainerDTO getContainer(String containerId) {
        return getContainerClient().get(containerId);
    }


    public String getAppUrl(String containerId) {
        ContainerDTO container = getContainer(containerId);
        Log.info("Container info received from orch-api for id: " + containerId + " - " + container);

        if (container == null) {
            throw new IllegalArgumentException("Container with ID " + containerId + " not found.");
        }

        /*Integer port = Optional.of(List.of(container.getPorts()).getFirst())
                .map(ContainerPortDTO::getPrivatePort)
                .orElse(9000);*/ // FIX TO 9000
        // Why?

        int port = 9000; // TODO: hardcoded port?
        String name = Optional.of(List.of(container.getNames()).getFirst())
                .orElseThrow(() -> new IllegalArgumentException("Container name not found."));

        if (name.startsWith("/")) {
            name = name.substring(1); // Remove leading slash if present
        }

        return String.format("http://%s:%d", name, port);

    }

}
