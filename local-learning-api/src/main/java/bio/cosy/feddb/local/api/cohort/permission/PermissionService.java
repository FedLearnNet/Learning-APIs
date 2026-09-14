package bio.cosy.feddb.local.api.cohort.permission;

import io.quarkus.security.Authenticated;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.List;

@Path("/permissions")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Authenticated
//@RolesAllowed("admin")
@Tag(name = "Permission", description = "Service for managing Permission operations")
public interface PermissionService {

    @GET
    @Operation(summary = "List all permissions", description = "Returns a list of all permission records")
    @APIResponse(responseCode = "200", description = "List of all permissions",
            content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = PermissionDTO.class)))
    @Transactional
    List<PermissionDTO> list();

    @GET
    @Path("/{id}")
    @Operation(summary = "Retrieve a single permission", description = "Returns a single permission by its id")
    @APIResponse(responseCode = "200", description = "Permission found",
            content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = PermissionDTO.class)))
    @APIResponse(responseCode = "404", description = "Permission not found")
    @Transactional
    PermissionDTO retrieve(@PathParam("id") Long id);

    @POST
    @Operation(summary = "Create a new permission", description = "Creates a new permission record")
    @APIResponse(responseCode = "201", description = "Permission created",
            content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = PermissionDTO.class)))
    @APIResponse(responseCode = "400", description = "Invalid data")
    @APIResponse(responseCode = "404", description = "Cohort not found")
    @Transactional
    Response create(@RequestBody @Valid PermissionDTO createDTO);

    @PUT
    @Path("/{id}")
    @Operation(summary = "Update a permission", description = "Updates an existing permission record")
    @APIResponse(responseCode = "200", description = "Permission updated")
    @APIResponse(responseCode = "400", description = "Invalid data")
    @APIResponse(responseCode = "404", description = "Permission not found")
    @Transactional
    PermissionDTO update(
            @PathParam("id") Long id,
            @Valid PermissionDTO updateDTO);

    @DELETE
    @Path("/{id}")
    @Operation(summary = "Delete a permission", description = "Deletes a permission record by its UUID")
    @APIResponse(responseCode = "200", description = "Permission deleted")
    @APIResponse(responseCode = "404", description = "Permission not found")
    @Transactional
    Response delete(@PathParam("id") Long id);
}
