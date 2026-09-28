package de.unihamburg.daibetes.health;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@Path("/healthz")
@RegisterRestClient(configKey = "relay-health")
public interface RelayHealthClient {
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    String health();
}
