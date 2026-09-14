package bio.cosy.feddb.local.api.workflow;

import bio.cosy.feddb.core.api.workflow.WorkflowCreateDTO;
import bio.cosy.feddb.core.api.workflow.WorkflowDTO;
import bio.cosy.feddb.core.base.ValidationGroups;
import io.quarkus.security.Authenticated;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.groups.ConvertGroup;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;


@Path("/workflow")
@Produces("application/json")
@Consumes("application/json")
@Authenticated
//@RolesAllowed("admin")
@Tag(name = "Workflow", description = "Service for managing WorkflowS operations")
interface WorkflowService {


    @GET
    @Path("/{id}")
    @Operation(summary = "Retrieve a single workflow")
    @APIResponse(responseCode = "200", description = "Workflow found")
    @APIResponse(responseCode = "404", description = "Workflow not found")
    @Transactional
    WorkflowDTO retrieve(@PathParam("id") Long id);

    @POST
    @Operation(summary = "Create a new workflow")
    @APIResponse(responseCode = "201", description = "Workflow created",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = WorkflowDTO.class)
            ))
    @APIResponse(responseCode = "400", description = "Invalid data")
    @Transactional
    Response create(@RequestBody @Valid @ConvertGroup(to = ValidationGroups.Post.class) WorkflowCreateDTO createDTO);

    @PUT
    @Path("/{id}")
    @Operation(summary = "Update a workflow")
    @APIResponse(responseCode = "200", description = "Workflow updated")
    @APIResponse(responseCode = "400", description = "Invalid data")
    @APIResponse(responseCode = "404", description = "Workflow updated not found")
    @Transactional
    WorkflowDTO update(@PathParam("id") Long id, @Valid @ConvertGroup(to = ValidationGroups.Put.class) @RequestBody WorkflowDTO updateDTO);

    @DELETE
    @Path("/{id}")
    @Operation(summary = "Delete a workflow")
    @APIResponse(responseCode = "200", description = "Workflow deleted")
    @APIResponse(responseCode = "404", description = "Workflow not found")
    @Transactional
    Response delete(@PathParam("id") Long id);

}
