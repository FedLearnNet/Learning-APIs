package bio.cosy.feddb.local.services.orch;

import bio.cosy.feddb.core.services.orch.clients.VolumeServiceClient;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@RegisterRestClient(configKey = "orch-docker-service")
public interface OrchVolumeServiceClient extends VolumeServiceClient {

}
