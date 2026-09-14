package bio.cosy.feddb.local.api.information;

import io.quarkus.security.Authenticated;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Path("information")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Authenticated
@Tag(name = "UserInformationService", description = "Service for getting Base information for the user")
public interface UserInformationService {
    @GET
    @Operation(summary = "Retrieve User infromations")
    @APIResponse(responseCode = "200", description = "UserInformationDTO found")
    @APIResponse(responseCode = "404", description = "UserInformationDTO not found")
    UserInformationDTO getBaseUserInformation();
}
