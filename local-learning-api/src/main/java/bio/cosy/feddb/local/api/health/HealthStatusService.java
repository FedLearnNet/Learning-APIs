package bio.cosy.feddb.local.api.health;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Path("/health")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Health", description = "Health of this service and the services it depends on")
public interface HealthStatusService {

    @GET
    @Path("/ready")
    @Operation(summary = "Readiness report", description = "Same payload as /q/health/ready, but needs a login")
    @APIResponse(responseCode = "200", description = "All checks are up")
    @APIResponse(responseCode = "503", description = "At least one check is down, the body still contains all checks")
    Response ready();
}
