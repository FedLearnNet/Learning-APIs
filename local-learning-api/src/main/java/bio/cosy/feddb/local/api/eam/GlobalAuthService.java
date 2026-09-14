package bio.cosy.feddb.local.api.eam;

import io.quarkus.security.Authenticated;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Path("/global/auth")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Global Auth", description = "Global authentication settings for the local client")
public interface GlobalAuthService {

    String GLOBAL_AUTHORIZATION_HEADER = "X-Global-Authorization";

    @POST
    @Path("login")
    @Authenticated
    @Operation(summary = "Log in to global auth", description = "Uses the provided global username and password for global WebSocket and HTTP calls, then reconnects the WebSocket as that user. No token is returned by this endpoint.")
    @APIResponse(responseCode = "204", description = "Global user login accepted")
    @APIResponse(responseCode = "400", description = "Missing global username or password")
    Response login(GlobalAuthLoginDTO login);

    @POST
    @Path("authorization")
    @Authenticated
    @Operation(summary = "Use global user auth header", description = "Updates global WebSocket and HTTP calls to use a provided global user authorization header. Prefer /global/auth/login when the frontend should not handle tokens.")
    @APIResponse(responseCode = "204", description = "Global user authorization accepted")
    @APIResponse(responseCode = "400", description = "Missing global user authorization")
    Response useAuthorization(
            @Parameter(description = "Global user authorization header, for example: Bearer eyJ...")
            @HeaderParam(GLOBAL_AUTHORIZATION_HEADER) String authorizationHeader
    );

    @DELETE
    @Path("login")
    @Authenticated
    @Operation(summary = "Clear global user login", description = "Clears runtime global user auth and falls back to application properties if configured.")
    @APIResponse(responseCode = "204", description = "Global user login cleared")
    Response clearLogin();
}
