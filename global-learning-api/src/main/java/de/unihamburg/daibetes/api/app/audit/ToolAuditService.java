package de.unihamburg.daibetes.api.app.audit;

import bio.cosy.feddb.core.api.app.ToolAuditDTO;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.ParameterIn;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.resteasy.reactive.ResponseStatus;

import java.util.List;

@Path("apps/audit")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RolesAllowed({"Admin", "Auditor"})
@Tag(name = "Audit", description = "Service for managing tool Audit")
public interface ToolAuditService {
    @GET
    @Operation(summary = "List audit events", description = "Lists audit events with optional filterin")
    @APIResponse(
            responseCode = "200",
            description = "audit list")
    List<ToolAuditDTO> list(
            @Parameter(in = ParameterIn.QUERY, description = "Filter by appId")
            @QueryParam("toolId") Long toolId,
            @Parameter(in = ParameterIn.QUERY, description = "Filter by actorId (Keycloak sub)")
            @QueryParam("actorId") String keycloakId
    );

    @GET
    @Path("pending")
    @Operation(summary = "List pending audit events", description = "Lists pending audit events ")
    @APIResponse(
            responseCode = "200",
            description = "audit pending list")
    List<ToolAuditPendingDTO> listPending();


    @GET
    @Path("{id}")
    @Operation(summary = "Get audit event", description = "Returns one audit event by ID.")
    @APIResponse(
            responseCode = "200",
            description = "Audit event")
    @APIResponse(responseCode = "404", description = "Not found")
    ToolAuditDTO get(@PathParam("id") Long id);


    @GET
    @Path("tool-version/{id}")
    @Operation(summary = "Get audit event", description = "Returns one audit event by ID.")
    @APIResponse(
            responseCode = "200",
            description = "Audit event")
    @APIResponse(responseCode = "404", description = "Not found")
    ToolAuditCombinationDTO getByVersion(@PathParam("id") Long id);

    @POST
    @Operation(summary = "Create audit event", description = "Creates a new audit event (usually internal/service usage).")
    @APIResponse(
            responseCode = "201",
            description = "Created")
    @APIResponse(responseCode = "400", description = "Validation error")
    @ResponseStatus(201)
    ToolAuditDTO create(ToolAuditDTO dto);

    @PUT
    @Path("{id}")
    @Operation(summary = "Update audit event", description = "Updates an audit event (rare; typically only allowed for admin corrections).")
    @APIResponse(
            responseCode = "200",
            description = "Updated")
    @APIResponse(responseCode = "404", description = "Not found")
    @APIResponse(responseCode = "409", description = "Conflict / optimistic lock")
    ToolAuditDTO update(@PathParam("id") Long id, ToolAuditDTO dto);

    @DELETE
    @Path("{id}")
    @Operation(summary = "Delete audit event", description = "Deletes an audit event (compliance-sensitive; usually disabled or admin-only).")
    @APIResponse(responseCode = "204", description = "Deleted")
    @APIResponse(responseCode = "404", description = "Not found")
    Response delete(@PathParam("id") Long id);
}
