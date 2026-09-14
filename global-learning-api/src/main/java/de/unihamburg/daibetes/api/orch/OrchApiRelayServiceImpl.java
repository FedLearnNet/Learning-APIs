package de.unihamburg.daibetes.api.orch;

import bio.cosy.feddb.core.services.orch.dto.*;
import de.unihamburg.daibetes.services.OrchContainerServiceClient;
import de.unihamburg.daibetes.services.OrchDockerServiceClient;
import de.unihamburg.daibetes.services.OrchVolumeServiceClient;
import io.smallrye.mutiny.Multi;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.util.List;

@ApplicationScoped
public class OrchApiRelayServiceImpl implements OrchApiRelayService {

    @Inject
    @RestClient
    OrchContainerServiceClient containerClient;

    @Inject
    @RestClient
    OrchVolumeServiceClient volumeClient;

    @Inject
    @RestClient
    OrchDockerServiceClient dockerClient;

    @Override
    public InfoDTO dockerInfo() {
        return dockerClient.getInfo();
    }

    @Override
    public List<ContainerDTO> listRunning() {
        return containerClient.list();
    }

    @Override
    public ContainerDTO getRunning(String id) {
        return containerClient.get(id);
    }

    @Override
    public Multi<String> streamLogs(String id) {
        return containerClient.getLogsStream(id);
    }

    @Override
    public List<ContainerRunDTO> listRuns() {
        return containerClient.listRuns();
    }

    @Override
    public ContainerRunDTO getRun(Long id) {
        return containerClient.getRun(id);
    }

    @Override
    public List<ContainerLogDTO> getRunLogs(Long id) {
        return containerClient.getRunLogs(id);
    }

    @Override
    public List<InspectVolumeResponseDTO> listVolumes() {
        return volumeClient.listVolumes();
    }

    @Override
    public InspectVolumeResponseDTO getVolume(String name) {
        return volumeClient.getVolume(name);
    }

    @Override
    public Response removeVolume(String name) {
        return volumeClient.removeVolume(name);
    }

    @Override
    public List<ContainerDTO> listFeatureCloud() {
        return containerClient.listFeatureCloud();
    }

    @Override
    public Response cleanupContainers(List<String> containerIds, boolean cleanup) {
        return containerClient.cleanupContainers(containerIds, cleanup);
    }
}
