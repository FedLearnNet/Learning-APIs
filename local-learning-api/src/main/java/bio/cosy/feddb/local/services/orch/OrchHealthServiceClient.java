package bio.cosy.feddb.local.services.orch;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@Path("/q/health")
@RegisterRestClient(configKey = "orch-docker-service")
public interface OrchHealthServiceClient {
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    String ready(); // roher JSON-String reicht für Status/Code
}
