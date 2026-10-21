package bio.cosy.feddb.local.api.info;

import jakarta.annotation.security.PermitAll;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Path("/info")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Info", description = "Information about this Client and its Platform, e.g. their versions")
public interface InfoService {

    @GET
    @PermitAll
    @Operation(summary = "Client information", description = "Public. Versions of this Client and its Platform and the Platform frontend URL")
    @APIResponse(responseCode = "200", description = "Client information, globalVersion is empty if the Platform is not reachable")
    LocalInfoDTO getInfo();
}
