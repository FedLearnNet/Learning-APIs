package bio.cosy.feddb.core.api.datamodler.schema;

import bio.cosy.feddb.core.base.PagedResponse;
import io.smallrye.mutiny.Uni;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.resteasy.reactive.ResponseStatus;

import java.util.List;
import java.util.UUID;

import static org.jboss.resteasy.reactive.RestResponse.StatusCode.CREATED;

// import jakarta.annotation.security.RolesAllowed;

@Path("/schemas")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Schema", description = "Service for schema operations")
public interface SchemaService {

    @GET
    @Operation(
            summary = "List all schema nodes (flat)",
            description = "Lists all schema nodes without the hierarchy of nodes."
    )
    @APIResponses({
            @APIResponse(
                    responseCode = "200",
                    description = "List of schema nodes",
                    content = @Content(schema = @Schema(implementation = SchemaNodeDTO.class, type = SchemaType.ARRAY))
            )
    })
        // @RolesAllowed({"schema-read", "schema-write"})
    Uni<PagedResponse<SchemaNodeDTO>> list(
            @Parameter(
                    name = "page",
                    description = "Page number (1-based)"
            )
            @DefaultValue("0") @QueryParam("page") int page,
            @Parameter(
                    name = "page_size",
                    description = "Page size"
            )
            @DefaultValue("200") @QueryParam("page_size") int pageSize);

    @GET
    @Path("/head")
    @Operation(
            summary = "List all schema head nodes",
            description = "Lists all schemas (JUST HEAD aka the root nodes), optionally filtered by ontologyId."
    )
    @APIResponses({
            @APIResponse(
                    responseCode = "200",
                    description = "List of schema head (root) nodes",
                    content = @Content(schema = @Schema(implementation = SchemaNodeDetailDTO.class, type = SchemaType.ARRAY))
            )
    })
    Uni<List<SchemaNodeDetailDTO>> getHead(
            @Parameter(
                    name = "ontologyId",
                    description = "Ontology ID for filter",
                    required = false
            )
            @QueryParam("ontologyId") UUID ontologyId
    );

    @POST
    @Operation(
            summary = "Create a new schema node",
            description = "Creates a new schema node."
    )
    @APIResponses({
            @APIResponse(
                    responseCode = "201",
                    description = "Schema created",
                    content = @Content(schema = @Schema(implementation = SchemaNodeDTO.class))
            ),
            @APIResponse(
                    responseCode = "400",
                    description = "Invalid data or schema already exists"
            )
    })
    @ResponseStatus(CREATED)
        // @RolesAllowed({"schema-create"})
    Uni<SchemaNodeDTO> create(
            @Parameter(
                    description = "Payload for creating a schema node",
                    required = true
            )
            @Valid SchemaNodeDTO dto
    );

    @POST
    @Path("/head")
    @Operation(
            summary = "Create a new schema head node",
            description = "Creates a new schema head (root) node."
    )
    @APIResponses({
            @APIResponse(
                    responseCode = "201",
                    description = "Schema head created",
                    content = @Content(schema = @Schema(implementation = SchemaNodeDTO.class))
            ),
            @APIResponse(
                    responseCode = "400",
                    description = "Invalid data or schema already exists"
            )
    })
    @ResponseStatus(CREATED)
    Uni<SchemaNodeDTO> createHead(
            @Parameter(
                    description = "Payload for creating a schema head node")
            @Valid  SchemaNodeDTO dto
    );

    @GET
    @Path("/{id}")
    @Operation(
            summary = "Get a schema node by ID",
            description = "Retrieves a single schema node."
    )
    @APIResponses({
            @APIResponse(
                    responseCode = "200",
                    description = "Schema found",
                    content = @Content(schema = @Schema(implementation = SchemaNodeDTO.class))
            ),
            @APIResponse(
                    responseCode = "404",
                    description = "Schema not found"
            )
    })
    Uni<SchemaNodeDetailDTO> getById(
            @Parameter(description = "Schema ID", required = true)
            @PathParam("id") UUID id
    );

    @GET
    @Path("/{id}/children")
    @Operation(
            summary = "Get children schema nodes",
            description = "Retrieves for a single schema node all children nodes."
    )
    @APIResponses({
            @APIResponse(
                    responseCode = "200",
                    description = "List of child schema nodes",
                    content = @Content(schema = @Schema(implementation = SchemaNodeDTO.class, type = SchemaType.ARRAY))
            ),
            @APIResponse(
                    responseCode = "404",
                    description = "Schema not found"
            )
    })
    Uni<List<SchemaNodeDTO>> getChildren(
            @Parameter(description = "Schema ID", required = true)
            @PathParam("id") UUID id
    );

    @GET
    @Path("/{id}/head")
    @Operation(
            summary = "Get root head for given schema",
            description = "Get head for given schema (root node, not parent)."
    )
    @APIResponses({
            @APIResponse(
                    responseCode = "200",
                    description = "Root schema found",
                    content = @Content(schema = @Schema(implementation = SchemaNodeDTO.class))
            ),
            @APIResponse(
                    responseCode = "404",
                    description = "Schema not found"
            )
    })
    Uni<SchemaNodeDTO> getHeadForSchema(
            @Parameter(description = "Schema ID")
            @PathParam("id") UUID id
    );

    @GET
    @Path("/{id}/sub-structure")
    @Operation(
            summary = "Get sub-structure for a schema",
            description = "Retrieves a schema with its hierarchy and relevant ontology and data type IDs, but no further details."
    )
    @APIResponses({
            @APIResponse(
                    responseCode = "200",
                    description = "Schema sub-structure",
                    content = @Content(schema = @Schema(implementation = SchemaStructureDTO.class))
            ),
            @APIResponse(
                    responseCode = "404",
                    description = "Schema not found"
            )
    })
    Uni<SchemaStructureDTO> getSubStructure(
            @Parameter(description = "Schema ID")
            @PathParam("id") UUID id
    );

    @PUT
    @Path("/{id}")
    @Operation(
            summary = "Update a schema node",
            description = "Updates an existing schema node (full update)."
    )
    @APIResponses({
            @APIResponse(
                    responseCode = "200",
                    description = "Schema updated",
                    content = @Content(schema = @Schema(implementation = SchemaNodeDTO.class))
            ),
            @APIResponse(
                    responseCode = "400",
                    description = "Invalid data"
            ),
            @APIResponse(
                    responseCode = "404",
                    description = "Schema not found"
            )
    })
    Uni<SchemaNodeDTO> update(
            @Parameter(description = "Schema ID", required = true)
            @PathParam("id") UUID id,
            @Parameter(description = "Updated schema payload", required = true)
            @Valid  SchemaNodeDTO dto
    );


    @DELETE
    @Path("/{id}")
    @Operation(
            summary = "Delete a schema node",
            description = "Deletes a schema node. Does NOT delete children nodes!"
    )
    @APIResponses({
            @APIResponse(
                    responseCode = "200",
                    description = "Schema deleted successfully"
            ),
            @APIResponse(
                    responseCode = "404",
                    description = "Schema not found"
            )
    })
    Uni<Response> delete(
            @Parameter(description = "Schema ID")
            @PathParam("id") UUID id
    );
}
