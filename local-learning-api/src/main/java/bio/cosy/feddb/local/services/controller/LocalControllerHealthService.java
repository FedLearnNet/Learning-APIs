package bio.cosy.feddb.local.services.controller;


import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@Path("/healthz")
@RegisterRestClient(configKey = "controller-api")
public interface LocalControllerHealthService {
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    String health();

}
