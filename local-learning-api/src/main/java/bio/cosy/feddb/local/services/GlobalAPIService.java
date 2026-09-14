package bio.cosy.feddb.local.services;


import bio.cosy.feddb.core.api.app.FederatedAppDetailDTO;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.annotation.RegisterProvider;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@RegisterRestClient(configKey = "global-api")
@RegisterProvider(GlobalAPIAuthRequestFilter.class)
@Path("/apps")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public interface GlobalAPIService {

    @GET
    @Path("/{appId}")
    FederatedAppDetailDTO getApp(@PathParam("appId") Long appId);
}
