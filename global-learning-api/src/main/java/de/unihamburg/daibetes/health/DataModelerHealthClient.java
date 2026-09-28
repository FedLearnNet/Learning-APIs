package de.unihamburg.daibetes.health;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@Path("/q/health")
@RegisterRestClient(configKey = "data-modeler-health")
public interface DataModelerHealthClient {
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    String health();
}
