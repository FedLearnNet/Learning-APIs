package bio.cosy.feddb.local.api.learning;


import bio.cosy.feddb.core.api.app.FederatedAppDetailDTO;
import io.quarkus.security.Authenticated;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Path("/learning/global")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Authenticated
//@RolesAllowed("admin")
@Tag(name = "GlobalAppService", description = "Service for acccess global learning api")
public interface GlobalAppDetailService {

    @GET
    @Path("app/{id}")
    @Operation(summary = "Retrieves an app by its primary key")
    @APIResponses(value = {
            @APIResponse(responseCode = "200", description = "See global service"),
            @APIResponse(responseCode = "404", description = "Schema not found")
    })
    public FederatedAppDetailDTO getAppDetail(@PathParam("id") Long id);
}
