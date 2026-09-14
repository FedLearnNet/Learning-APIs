package de.unihamburg.daibetes.api.query;

import bio.cosy.feddb.core.api.query.QueryDTO;
import bio.cosy.feddb.core.api.query.QueryDetailDTO;
import bio.cosy.feddb.core.base.ValidationGroups;
import io.quarkus.security.Authenticated;
import io.smallrye.mutiny.Multi;
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
import org.jboss.resteasy.reactive.RestStreamElementType;

import java.util.List;


@Path("/query")
@Produces("application/json")
@Consumes("application/json")
@Authenticated
//@RolesAllowed("admin")
@Tag(name = "Query", description = "Service for managing Query operations")
public interface QueryService {

    @GET
    @Operation(summary = "List all queries")
    @APIResponse(responseCode = "200", description = "List of Queries")
    @Transactional
    List<QueryDTO> list();

    @GET
    @Path("/sse")
    @Operation(summary = "List all queries")
    @APIResponse(responseCode = "200", description = "List of Queries")
    @Transactional
    @RestStreamElementType(MediaType.APPLICATION_JSON)
    Multi<QueryDTO> listSSE();

    @GET
    @Path("/clients")
    @Operation(summary = "List all connected clients")
    @APIResponse(responseCode = "200", description = "List of clients")
    List<String> getAllClients();

    @GET
    @Path("/{id}")
    @Operation(summary = "Retrieve a single query")
    @APIResponse(responseCode = "200", description = "Query found")
    @APIResponse(responseCode = "404", description = "Query found")
    @Transactional
    QueryDetailDTO retrieve(@PathParam("id") Long id);

    @POST
    @Operation(summary = "Create a new Query")
    @APIResponse(responseCode = "201", description = "Query created",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = QueryDTO.class)
            ))
    @APIResponse(responseCode = "400", description = "Invalid data")
    @Transactional
    Response create(@RequestBody @Valid @ConvertGroup(to = ValidationGroups.Post.class) QueryCreateDTO createDTO);

    @PUT
    @Path("/{id}")
    @Operation(summary = "Update a Query")
    @APIResponse(responseCode = "200", description = "Query updated")
    @APIResponse(responseCode = "400", description = "Invalid data")
    @APIResponse(responseCode = "404", description = "Query not found")
    @Transactional
    QueryDTO update(@PathParam("id") Long id, @Valid @ConvertGroup(to = ValidationGroups.Put.class) @RequestBody QueryDTO updateDTO);


    @POST
    @Path("/{id}/fire")
    @Operation(summary = "Update a Query")
    @APIResponse(responseCode = "200", description = "Query fired")
    @APIResponse(responseCode = "400", description = "Invalid data")
    @APIResponse(responseCode = "404", description = "Query not found")
    @Transactional
    QueryDTO fireQuery(@PathParam("id") Long id);

    @POST
    @Path("/{id}/fire-data-statistics")
    @Operation(summary = "Update a Query")
    @APIResponse(responseCode = "200", description = "Query fired")
    @APIResponse(responseCode = "400", description = "Invalid data")
    @APIResponse(responseCode = "404", description = "Query not found")
    @Transactional
    QueryDTO fireDataStatistics(@PathParam("id") Long id);

    @POST
    @Path("/fire")
    @Operation(summary = "Create and run")
    @APIResponse(responseCode = "200", description = "Query fired")
    @APIResponse(responseCode = "400", description = "Invalid data")
    @Transactional
    QueryDTO createAndRun(@RequestBody @Valid @ConvertGroup(to = ValidationGroups.Post.class) QueryCreateDTO createDTO);


    @DELETE
    @Path("/{id}")
    @Operation(summary = "Delete a Query")
    @APIResponse(responseCode = "200", description = "Query deleted")
    @APIResponse(responseCode = "404", description = "Query not found")
    @Transactional
    Response delete(@PathParam("id") Long id);

}
