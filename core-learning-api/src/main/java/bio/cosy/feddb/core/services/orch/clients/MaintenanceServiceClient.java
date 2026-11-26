package bio.cosy.feddb.core.services.orch.clients;

import bio.cosy.feddb.core.services.orch.dto.MaintenanceMode;
import bio.cosy.feddb.core.services.orch.dto.MaintenanceStatusDTO;
import io.smallrye.mutiny.Multi;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;
import org.jboss.resteasy.reactive.RestStreamElementType;

@Path("/maintenance")
@RegisterRestClient(configKey = "orch-docker-service")
@Produces("application/json")
public interface MaintenanceServiceClient {
    @GET
    @Produces(MediaType.SERVER_SENT_EVENTS)
    @RestStreamElementType(MediaType.APPLICATION_JSON)
    Multi<MaintenanceStatusDTO> status();

    @POST
    @Path("{mode}")
    @Produces(MediaType.SERVER_SENT_EVENTS)
    @RestStreamElementType(MediaType.APPLICATION_JSON)
    Multi<MaintenanceStatusDTO> start(@PathParam("mode") MaintenanceMode mode, @QueryParam("version") String version,
                                      @QueryParam("all") boolean all, @QueryParam("backup") String backup);
}
