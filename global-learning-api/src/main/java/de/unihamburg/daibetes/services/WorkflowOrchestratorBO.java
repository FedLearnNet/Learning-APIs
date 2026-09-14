package de.unihamburg.daibetes.services;

import bio.cosy.feddb.core.services.orch.WorkflowOrchestrator;
import bio.cosy.feddb.core.services.orch.clients.ContainerServiceClient;
import bio.cosy.feddb.core.services.orch.clients.DockerServiceClient;
import bio.cosy.feddb.core.services.orch.clients.VolumeServiceClient;
import bio.cosy.feddb.core.services.orch.clients.WorkflowServiceClient;
import io.quarkus.logging.Log;
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
    protected VolumeServiceClient getVolumeClient() {
        return volumeClient;
    }

    @Override
    protected DockerServiceClient getDockerClient() {
        return dockerClient;
    }

    @Override
    protected WorkflowServiceClient getWorkflowClient() {
        return workflowClient;
    }

    public List<File> getFiles(Long workflowId, Long workflowNodeId) {
        try (Response r = getVolumeClient().downloadFilesIds(workflowId, workflowNodeId)) {
            if (r.getStatus() != 200) {
                throw new RuntimeException("Download failed with status: " + r.getStatus());
            }

            Path tmpDir = Files.createTempDirectory(
                    "vol-download-" + workflowId + "-" + workflowNodeId + "-");

            try (InputStream is = r.readEntity(InputStream.class)) {
                return FileHelper.unzip(is, tmpDir);
            }
        } catch (Exception e) {
            Log.errorf(e, "Failed to download workflow orchestrator logs: %s", workflowId);
            return new ArrayList<>();
        }
    }
}
