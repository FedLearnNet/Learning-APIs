package de.unihamburg.daibetes.services;

import bio.cosy.feddb.core.api.info.InfoDTO;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

// public endpoint, no token needed
@RegisterRestClient(configKey = "data-modeler-service")
@Path("/info")
@Produces(MediaType.APPLICATION_JSON)
public interface DataModelerInfoService {

    @GET
    InfoDTO getInfo();
}
