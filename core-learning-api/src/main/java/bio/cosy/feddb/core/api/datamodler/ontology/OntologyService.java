package bio.cosy.feddb.core.api.datamodler.ontology;

import bio.cosy.feddb.core.base.PagedResponse;
import io.smallrye.mutiny.Uni;
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

@Path("/ontology")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Ontology", description = "Service for ontology operations")
public interface OntologyService {

    @GET
    @Operation(summary = "List all ontologies", description = "Lists all ontologies.")
    @APIResponses({
            @APIResponse(
                    responseCode = "200",
                    description = "List of ontologies"
            )
    })
        // @RolesAllowed({"ontology-read", "ontology-write"})
    Uni<PagedResponse<OntologyNodeDTO>> list(
            @Parameter(
                    name = "search",
                    description = "To search the ontologies"
            )
            @QueryParam("search") String search,
            @Parameter(
                    name = "page",
                    description = "Page number (1-based)"
            )
            @DefaultValue("0") @QueryParam("page") int page,
            @Parameter(
                    name = "page_size",
                    description = "Page size"
            )
            @DefaultValue("200") @QueryParam("page_size") int pageSize
    );

    @GET
    @Operation(summary = "search ontologies", description = "Search.")
    @APIResponses({
            @APIResponse(
                    responseCode = "200",
                    description = "List of ontologies",
                    content = @Content(schema = @Schema(implementation = OntologyNodeDTO.class, type = SchemaType.ARRAY))
            )
    })
    @Path("embedding/search")
        // @RolesAllowed({"ontology-read", "ontology-write"})
    Uni<List<OntologySearchResponseDTO>> searchEmbedding(
            @Parameter(
                    name = "search",
                    description = "To search the ontologies"
            )
            @QueryParam("search") String search,
            @Parameter(
                    name = "max_results",
                    description = "RAG max results"
            )
            @DefaultValue("3") @QueryParam("max-results") int maxResults
    );

    @GET
    @Path("/queryability")
    @Operation(
            summary = "List queryability ontologies",
            description = "Lists all ontologies for queryability, optionally filtered by clients."
    )
    @APIResponses({
            @APIResponse(
                    responseCode = "200",
                    description = "List of ontology queryability entries",
                    content = @Content(schema = @Schema(implementation = OntologyQueryAbilityDTO.class, type = SchemaType.ARRAY))
            )
    })
    Uni<List<OntologyQueryAbilityDTO>> listQueryability(
            @Parameter(
                    name = "filter_clients",
                    description = "A list of clients to filter by",
                    schema = @Schema(type = SchemaType.ARRAY, implementation = String.class)
            )
            @QueryParam("filter_clients") List<String> filterClients
    );


    @POST
    @Operation(summary = "Create a new ontology", description = "Creates a new ontology. In the edges only give either the target or source ontology ID. The given ontology will then be linked accordingly.")
    @APIResponses({
            @APIResponse(
                    responseCode = "201",
                    description = "Ontology created",
                    content = @Content(schema = @Schema(implementation = OntologyDTO.class))
            ),
            @APIResponse(
                    responseCode = "400",
                    description = "Invalid data"
            ),
    })
    @ResponseStatus(CREATED)
        // @RolesAllowed({"ontology-create"})
    Uni<OntologyDTO> create(
            @Parameter(
                    description = "Payload for creating an ontology",
                    required = true
            )
            CreateOntologyDTO dto
    );

    @GET
    @Path("/{id}")
    @Operation(summary = "Get an ontology by ID", description = "Retrieves a single ontology.")
    @APIResponses({
            @APIResponse(
                    responseCode = "200",
                    description = "Ontology found"
            ),
            @APIResponse(
                    responseCode = "404",
                    description = "Ontology not found"
            )
    })
    Uni<OntologyNodeDTO> getById(
            @Parameter(description = "Ontology ID", required = true)
            @PathParam("id") UUID id
    );

    @GET
    @Path("/ids")
    @Operation(summary = "Get an ontology by IDs", description = "Retrieves a list of ontologies.")
    @APIResponses({
            @APIResponse(
                    responseCode = "200",
                    description = "Ontology found"
            ),
            @APIResponse(
                    responseCode = "404",
                    description = "Ontology not found"
            )
    })
    Uni<List<OntologyNodeDTO>> getByIds(
            @Parameter(description = "Ontology IDs", required = true)
            @QueryParam("ids") List<UUID> ids
    );

    @GET
    @Path("/cui/{id}")
    @Operation(summary = "Get an ontology by ID", description = "Retrieves a single ontology.")
    @APIResponses({
            @APIResponse(
                    responseCode = "200",
                    description = "Ontology found"
            ),
            @APIResponse(
                    responseCode = "404",
                    description = "Ontology not found"
            )
    })
    Uni<OntologyNodeDTO> getViaCuiId(
            @Parameter(description = "CUI ID", required = true)
            @PathParam("id") String cui
    );

    @GET
    @Path("/{id}/children")
    @Operation(
            summary = "Get children ontologies",
            description = "Retrieves for a single ontology all child ontologies."
    )
    @APIResponses({
            @APIResponse(
                    responseCode = "200",
                    description = "List of child ontologies (empty list if none or not found)",
                    content = @Content(schema = @Schema(implementation = OntologyNodeDTO.class, type = SchemaType.ARRAY))
            )
    })
    Uni<List<OntologyNodeDTO>> getChildren(
            @Parameter(description = "Ontology ID", required = true)
            @PathParam("id") UUID id
    );

    @GET
    @Path("/{id}/edges")
    @Operation(
            summary = "Get edges ontologies",
            description = "Retrieves for a single ontology all edges."
    )
    @APIResponses({
            @APIResponse(
                    responseCode = "200",
                    description = "List of edges (empty list if none or not found)",
                    content = @Content(schema = @Schema(implementation = OntologyNodeDTO.class, type = SchemaType.ARRAY))
            )
    })
    Uni<List<OntologyEdgeDTO>> getEdges(
            @Parameter(description = "Ontology ID", required = true)
            @PathParam("id") UUID id
    );

    @GET
    @Path("/{id}/neighbors")
    @Operation(
            summary = "Get neighbors ontologies",
            description = "Retrieves for a single ontology all neighbors."
    )
    @APIResponses({
            @APIResponse(
                    responseCode = "200",
                    description = "ontology with the neighbors (empty list if none or not found)",
                    content = @Content(schema = @Schema(implementation = OntologyNodeDTO.class, type = SchemaType.ARRAY))
            )
    })
    Uni<OntologyDTO> getNeighbors(
            @Parameter(description = "Ontology ID", required = true)
            @PathParam("id") UUID id
    );


    @PUT
    @Path("/{id}")
    @Operation(summary = "Update an ontology", description = "Updates an existing ontology (full update).")
    @APIResponses({
            @APIResponse(
                    responseCode = "200",
                    description = "Ontology updated"),
            @APIResponse(
                    responseCode = "400",
                    description = "Invalid data"
            ),
            @APIResponse(
                    responseCode = "404",
                    description = "Ontology not found"
            )
    })
    Uni<OntologyNodeDTO> update(
            @Parameter(description = "Ontology ID", required = true)
            @PathParam("id") UUID id,
            @Parameter(description = "Updated ontology payload", required = true)
            OntologyNodeDTO dto
    );


    @DELETE
    @Path("/{id}")
    @Operation(summary = "Delete an ontology", description = "Deletes an ontology.")
    @APIResponses({
            @APIResponse(
                    responseCode = "200",
                    description = "Ontology deleted successfully"
            ),
            @APIResponse(
                    responseCode = "404",
                    description = "Ontology not found"
            )
    })
    Uni<Response> delete(
            @Parameter(description = "Ontology ID", required = true)
            @PathParam("id") UUID id
    );
}
