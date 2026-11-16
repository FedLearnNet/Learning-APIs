package bio.cosy.feddb.core.services.orch.clients;

import bio.cosy.feddb.core.services.orch.dto.SystemComputingInfoDTO;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@Path("/info/computing")
@RegisterRestClient(configKey = "orch-docker-service")
@Produces("application/json")
public interface ComputingInfoServiceClient {
    @GET
    SystemComputingInfoDTO getInfo();
}
