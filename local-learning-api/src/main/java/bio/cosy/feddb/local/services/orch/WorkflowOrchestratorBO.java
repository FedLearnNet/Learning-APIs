package bio.cosy.feddb.local.services.orch;

import bio.cosy.feddb.core.services.orch.WorkflowOrchestrator;
import bio.cosy.feddb.core.services.orch.clients.ContainerServiceClient;
import bio.cosy.feddb.core.services.orch.clients.DockerServiceClient;
import bio.cosy.feddb.core.services.orch.clients.VolumeServiceClient;
import bio.cosy.feddb.core.services.orch.clients.WorkflowServiceClient;
import io.quarkus.logging.Log;
import io.smallrye.mutiny.Multi;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import bio.cosy.feddb.core.helper.FileHelper;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
public class WorkflowOrchestratorBO extends WorkflowOrchestrator {

    @Inject
    @RestClient
    OrchContainerServiceClient containerClient;

    @Inject
    @RestClient
    OrchVolumeServiceClient volumeClient;

    @Inject
    @RestClient
    OrchDockerServiceClient dockerClient;

    @Inject
    @RestClient
    OrchWorkflowServiceClient workflowClient;

    @Override
    protected ContainerServiceClient getContainerClient() {
        return containerClient;
    }

    @Override
    protected WorkflowServiceClient getWorkflowClient() {
        return workflowClient;
    }

    @Override
    protected VolumeServiceClient getVolumeClient() {
        return volumeClient;
    }

    @Override
    protected DockerServiceClient getDockerClient() {
        return dockerClient;
    }


    public Multi<String> getLogsStream(String containerId) {
        return getContainerClient().getLogsStream(containerId);
    }

    public List<File> getFiles(Long workflowId, Long executionOrder) {
        Log.infof("Downloading output volume: workflowId=%d executionOrder=%d", workflowId, executionOrder);
        try (Response r = getVolumeClient().downloadFilesIds(workflowId, executionOrder)) {
            if (r.getStatus() != 200) {
                throw new IllegalStateException("Output download failed with status " + r.getStatus()
                        + ": " + r.readEntity(String.class));
            }

            Path tmpDir = Files.createTempDirectory(
                    "vol-download-" + workflowId + "-" + executionOrder + "-");

            try (InputStream is = r.readEntity(InputStream.class)) {
                return FileHelper.unzip(is, tmpDir);
            }
        } catch (Exception e) {
            Log.errorf(e, "Output download failed: workflowId=%d executionOrder=%d", workflowId, executionOrder);
            throw new IllegalStateException("Output download failed for workflow " + workflowId
                    + " node execution order " + executionOrder + ": " + e.getMessage(), e);
        }
    }

}
