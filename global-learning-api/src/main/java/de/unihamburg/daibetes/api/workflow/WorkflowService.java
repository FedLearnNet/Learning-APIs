package de.unihamburg.daibetes.api.workflow;

import bio.cosy.feddb.core.api.workflow.WorkflowCreateDTO;
import bio.cosy.feddb.core.api.workflow.WorkflowDTO;
import bio.cosy.feddb.core.base.ValidationGroups;
import de.unihamburg.daibetes.dto.URLDTO;
import io.quarkus.security.Authenticated;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.groups.ConvertGroup;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.resteasy.reactive.ResponseStatus;

import java.util.List;


@Path("/workflow")
@Produces("application/json")
@Consumes("application/json")
@Authenticated
//@RolesAllowed("admin")
@Tag(name = "Workflow", description = "Service for managing WorkflowS operations")
public interface WorkflowService {

    @GET
    @Operation(summary = "Retrieve a list of workflows for an app")
    @APIResponse(responseCode = "200", description = "List of workflows")
    @APIResponse(responseCode = "404", description = "Workflow or App not found")
    @Transactional
    List<WorkflowDTO> listWorkflows();

    @GET
    @Path("/list/app/{appId}")
    @Operation(summary = "Retrieve a list of workflows for an app")
    @APIResponse(responseCode = "200", description = "List of workflows")
    @APIResponse(responseCode = "404", description = "Workflow or App not found")
    @Transactional
    List<WorkflowDTO> listWorkflowForApp(@PathParam("appId") Long appId);

    @GET
    @Path("/{id}")
    @Operation(summary = "Retrieve a single workflow")
    @APIResponse(responseCode = "200", description = "Workflow found")
    @APIResponse(responseCode = "404", description = "Workflow not found")
    @Transactional
    WorkflowDTO retrieve(@PathParam("id") Long id);

    @POST
    @Operation(summary = "Create a new workflow")
    @APIResponse(responseCode = "201", description = "Workflow created")
    @APIResponse(responseCode = "400", description = "Invalid data")
    @Transactional
    @ResponseStatus(201)
    WorkflowDTO create(@RequestBody @Valid @ConvertGroup(to = ValidationGroups.Post.class) WorkflowCreateDTO createDTO);

    @POST
    @Path("new")
    @Operation(summary = "Create a new workflow")
    @APIResponse(responseCode = "201", description = "Workflow created")
    @APIResponse(responseCode = "400", description = "Invalid data")
    @Transactional
    @ResponseStatus(201)
    WorkflowDTO createNextEmpty();

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

    @POST
    @Path("/{id}/export")
    @Operation(summary = "Export an Workflow to json")
    @APIResponses({
            @APIResponse(responseCode = "200", description = "Workflow exported"),
            @APIResponse(responseCode = "400", description = "Invalid data"),
            @APIResponse(responseCode = "404", description = "Workflow not found")
    })
    @Transactional
    @Authenticated
    URLDTO saveWorkflowAsJson(@PathParam("id") Long id);
}
