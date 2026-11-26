package bio.cosy.feddb.core.api.maintenance;

import bio.cosy.feddb.core.security.Roles;
import bio.cosy.feddb.core.services.orch.dto.MaintenanceMode;
import io.smallrye.mutiny.Multi;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.resteasy.reactive.RestStreamElementType;

@Path("/admin/maintenance")
@RolesAllowed(Roles.ADMIN)
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Maintenance", description = "Upgrades and backups of this deployment, see 'Updating' in the documentation")
public interface MaintenanceService {

    @GET
    @Produces(MediaType.SERVER_SENT_EVENTS)
    @RestStreamElementType(MediaType.APPLICATION_JSON)
    @Operation(summary = "Installed version, the version that can be installed, what runs and the backups",
            description = "Streams the state now and, while flnet runs, on every change until it ends")
    Multi<MaintenanceDTO> loadMaintenance();

    @POST
    @Path("{mode}")
    @Produces(MediaType.SERVER_SENT_EVENTS)
    @RestStreamElementType(MediaType.APPLICATION_JSON)
    @Operation(summary = "Start an upgrade step or a backup and stream its state on every change until it ends",
            description = "PREPARE pulls the images while everything runs, APPLY installs "
            + "(no execution may be open), ROLLBACK goes back to the backup of the last update and loses everything since, "
            + "BACKUP dumps the databases and with all also copies configuration and secrets, RESTORE puts back a complete "
            + "backup of the installed version and starts again (no execution may be open). A refusal, e.g. as executions "
            + "are open, is a last state with error")
    Multi<MaintenanceDTO> startMaintenance(@PathParam("mode") MaintenanceMode mode, @QueryParam("version") String version,
                                           @QueryParam("all") boolean all, @QueryParam("backup") String backup);
}
