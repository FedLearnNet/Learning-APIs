package de.unihamburg.daibetes.api.app.startup;

import io.quarkus.security.Authenticated;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Path("/apps/tool-startup-generator")
@Produces("application/json")
@Consumes("application/json")
//@RolesAllowed("admin")
@Tag(name = "App", description = "Service for managing app code startup generation")
public interface ToolStartupGeneratorService {


    @POST
    @Path("{id}")
    @Produces("application/zip")
    @Operation(summary = "Generates a starter project for devs")
    @APIResponses({
            @APIResponse(responseCode = "200", description = "Startup Zip")
    })
    @Transactional
    @Authenticated
    Response generatorStartup(@PathParam("id") Long id, ToolStartupGeneratorCreateDTO createDTO);
}
