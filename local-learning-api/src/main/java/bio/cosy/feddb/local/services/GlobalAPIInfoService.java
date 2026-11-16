package bio.cosy.feddb.local.services;

import bio.cosy.feddb.core.api.info.InfoDTO;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@RegisterRestClient(configKey = "global-api")
@Path("/info")
@Produces(MediaType.APPLICATION_JSON)
public interface GlobalAPIInfoService {

    @GET
    InfoDTO getInfo();
}
