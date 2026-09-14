package bio.cosy.feddb.local.api.schema;

import io.quarkus.security.Authenticated;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.List;
import java.util.UUID;

// This class provides services that query the GLOBAL schema database.
// This is NOT the same then the CohortSchemas, as they only represent a certain version
// of the global schema. This also means that the relevant SchemaNodeEntities are
// managed by the CohortService and using the SchemaNodeBO directly!
@Path(SchemaService.PATH)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Authenticated
@Tag(name = "SchemaService", description = "Service for reading the global schema")
public interface SchemaService {
    public static final String PATH = "/schema";

    @GET
    @Operation(summary = "List all global schema root nodes", description = "Returns a list of all global schema root nodes. " +
            "Please use the id field, not the global schema id as a new id is given for any new version, " +
            "while the global schema id is reused. The childNodes are null here, if you need them load only these schema root retrieve_dyn_form")
    @APIResponse(responseCode = "200", description = "List of all global schema root nodes")
    @Transactional
    List<LocalSchemaRootNodeDTO> getGlobalRootNodes();


    @GET
    @Path("/{id}/retrieve_dyn_form")
    @Operation(summary = "Get the complete global schema by the global root schema nodes ID",
            description = "Returns a global schema by the global root schema node ID")
    @APIResponse(responseCode = "200", description = "Global schema found")
    @APIResponse(responseCode = "404", description = "Schema not found")
    LocalSchemaRootNodeDTO getGlobalSchemaFormInfo(@PathParam("id") UUID id);
    // Must use a root node ID here

    @GET
    @Path("/{id}")
    @Operation(summary = "Get the saved local schema by the internal local id",
            description = "Returns a schema by the internal local id")
    @APIResponse(responseCode = "200", description = "schema found")
    @APIResponse(responseCode = "404", description = "Schema not found")
    LocalSchemaNodeDTO getDetailById(@PathParam("id") Long id);

    @GET
    @Path("/cohort/{cohortId}")
    @Operation(summary = "List all local schema nodes by cohort", description = " List all local schema nodes by cohort")
    @APIResponse(responseCode = "200", description = "List all local schema nodes by cohort")
    @Transactional
    List<LocalSchemaNodeDTO> getSchemaNodesForCohort(@PathParam("cohortId") Long cohortId);

    @GET
    @Path("/project/{projectId}")
    @Operation(summary = "List all local schema nodes by project", description = " List all local schema nodes by project")
    @APIResponse(responseCode = "200", description = "List all local schema nodes by project")
    @Transactional
    List<LocalSchemaNodeDTO> getSchemaNodesForProject(@PathParam("projectId") Long projectId);

    @GET
    @Path("/cohort/{cohortId}/nested")
    @Operation(summary = "List all local schema nodes by cohort nested", description = " List all local schema nodes by cohort nested")
    @APIResponse(responseCode = "200", description = "List all local schema nodes by cohort nested")
    @Transactional
    LocalSchemaRootNodeDTO getSchemaNodesNestedForCohort(@PathParam("cohortId") Long cohortId);
}
