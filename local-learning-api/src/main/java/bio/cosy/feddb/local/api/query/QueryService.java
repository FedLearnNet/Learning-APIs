package bio.cosy.feddb.local.api.query;

import  io.quarkus.security.Authenticated;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.List;

@Path("/query")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Authenticated
//@RolesAllowed("admin")
@Tag(name = "Query", description = "Service for receiving Queries")
public interface QueryService {

    @GET
    @Operation(summary = "List all Queries", description = "Returns a list of all Queries records")
    @APIResponse(responseCode = "200", description = "List of all Queries")
    @Transactional
    List<LocalQueryDTO> list();

    @GET
    @Path("/{id}")
    @Operation(summary = "Retrieve a single Query", description = "Returns a single Queries")
    @APIResponse(responseCode = "200", description = "Query found")
    @APIResponse(responseCode = "404", description = "Query not found")
    @Transactional
    LocalQueryDTO retrieve(@PathParam("id") Long id);

}
