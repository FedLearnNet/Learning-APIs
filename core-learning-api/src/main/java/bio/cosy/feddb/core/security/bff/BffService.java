package bio.cosy.feddb.core.security.bff;

import io.quarkus.security.Authenticated;
import jakarta.annotation.security.PermitAll;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * For backend for frontend endpoints.
 */
@Path("/bff")
@Tag(name = "BFF", description = "Browser login of the frontend (backend-for-frontend)")
public interface BffService {

    /**
     * Not logged in: Quarkus redirects to Keycloak, Keycloak back to this endpoint, then redirect to the frontend.
     */
    @GET
    @Path("/login")
    @Authenticated // triggers the Keycloak login
    @Operation(summary = "Starts the browser login and redirects to the frontend afterwards")
    @APIResponse(responseCode = "303", description = "Logged in, redirect to the frontend")
    Response login();

    /**
     * Quarkus intercepts this path and also logs out at Keycloak. Without a session only the redirect to the frontend.
     */
    @GET
    @Path("/logout")
    @PermitAll
    @Operation(summary = "Logs the browser session out and redirects to the frontend")
    @APIResponse(responseCode = "303", description = "Redirect to the frontend")
    Response logout();

    @GET
    @Path("/me")
    @Authenticated
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Returns the logged in user of the browser session")
    @APIResponse(responseCode = "200", description = "User of the session")
    BffUserDTO me();
}
