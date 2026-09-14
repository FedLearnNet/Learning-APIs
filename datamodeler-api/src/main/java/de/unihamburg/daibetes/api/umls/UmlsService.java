package de.unihamburg.daibetes.api.umls;

import bio.cosy.feddb.core.base.PagedResponse;
import de.unihamburg.daibetes.api.umls.importer.ImportStatus;
import de.unihamburg.daibetes.api.umls.search.UMLSCytoscapeGraphElementDTO;
import de.unihamburg.daibetes.api.umls.search.UMLSDetailResultDTO;
import de.unihamburg.daibetes.api.umls.search.UMLSIdSourceDTO;
import de.unihamburg.daibetes.api.umls.search.UMLSSearchResultDTO;
import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.*;
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

import static org.jboss.resteasy.reactive.RestResponse.StatusCode.CREATED;

@Path("/umls")
@Produces("application/json")
@Consumes("application/json")
//@RolesAllowed("admin")
@Tag(name = "UMLS", description = "Service for umls operations")
public interface UmlsService {
    // Please note that in the Service, the umls import and embedding generation are separated into two endpoints.
    // However the automaticUmlsImportEmbedder on startup does automatically trigger umls import AND embedding
    @POST
    @Operation(summary = "Start UMLS import", description = "Start the UMLS integration process using files configured in application.properties (umls.import.mrconso and umls.import.mrrel). Does not trigger embedding generation, call POST /umls/embedding for that after the import finished.")
    @APIResponses({
            @APIResponse(responseCode = "200", description = "UMLS import started successfully"),
            @APIResponse(
                    responseCode = "400",
                    description = "UMLS import failed",
                    content = @Content(schema = @Schema(implementation = String.class))
            )
    })
    Uni<Response> create();

    @POST
    @Operation(summary = "Create all Embeddings", description = "Trigger embedding generation for all relevant nodes. Call this after an import via POST /umls finished, the POST /umls does NOT automatically trigger embedding generation.")
    @APIResponses({
            @APIResponse(responseCode = "200", description = "Ontology nodes embedded successfully"),
            @APIResponse(
                    responseCode = "400",
                    description = "UMLS embedding creation failed",
                    content = @Content(schema = @Schema(implementation = String.class))
            )
    })
    @Path("embedding")
    Uni<Response> createAllEmbedding();

    @GET
    @Path("/search")
    @Operation(
            summary = "Search for UMLS concepts",
            description = "Searches UMLS concepts by a search string and returns a paginated result."
    )
    @APIResponses({
            @APIResponse(
                    responseCode = "200",
                    description = "Paginated UMLS search results"
            )
    })
        // @RolesAllowed({"umls-read", "ontology-write"})
    Uni<PagedResponse<UMLSSearchResultDTO>> search(
            @Parameter(
                    name = "search_string",
                    description = "A search string for UMLS concepts"
            )
            @QueryParam("search_string") String searchString,
            @Parameter(
                    name = "page",
                    description = "Page number (1-based)"
            )
            @DefaultValue("0") @QueryParam("page") int page,
            @Parameter(
                    name = "page_size",
                    description = "Page size"
            )
            @DefaultValue("200") @QueryParam("page_size") int pageSize,
            @Parameter(
                    name = "sources",
                    description = "Comma-separated list of UMLS sources to filter by (e.g. SNOMEDCT_US,ICD10CM)"
            )
            @QueryParam("sources")   UMLSSources sources
    );

    @GET
    @Path("/{id}")
    @Operation(
            summary = "Get a UMLS concept",
            description = "Retrieves a single UMLS concept by ID and source."
    )
    @APIResponses({
            @APIResponse(
                    responseCode = "200",
                    description = "UMLS concept found",
                    content = @Content(schema = @Schema(implementation = UMLSDetailResultDTO.class))
            ),
            @APIResponse(
                    responseCode = "404",
                    description = "UMLS entity not found"
            )
    })
    Uni<UMLSDetailResultDTO> getItem(
            @Parameter(description = "UMLS concept identifier", required = true)
            @PathParam("id") String id,
            @Parameter(
                    name = "source",
                    description = "Terminology source (e.g. SNOMEDCT_US)"
            )
            @DefaultValue("SNOMEDCT_US") @QueryParam("source") UMLSSources source
    );

    @GET
    @Path("/{id}/cytoscape-graph")
    @Operation(
            summary = "Create Cytoscape graph for a UMLS concept",
            description = "Returns a list of Cytoscape graph elements for the given UMLS concept."
    )
    @APIResponses({
            @APIResponse(
                    responseCode = "200",
                    description = "List of Cytoscape elements",
                    content = @Content(schema = @Schema(implementation = UMLSCytoscapeGraphElementDTO.class, type = SchemaType.ARRAY))
            )
    })
    Uni<List<UMLSCytoscapeGraphElementDTO>> createCytoscapeGraph(
            @Parameter(description = "UMLS concept identifier", required = true)
            @PathParam("id") String id,
            @Parameter(
                    name = "source",
                    description = "Terminology source (e.g. SNOMEDCT_US)"
            )
            @DefaultValue("SNOMEDCT_US") @QueryParam("source") UMLSSources source
    );

    @POST
    @Path("/all-parents-graph")
    @Operation(
            summary = "Create all parents graph",
            description = "Creates the graph containing all parents of a UMLS concept."
    )
    @APIResponses({
            @APIResponse(
                    responseCode = "201",
                    description = "Graph created",
                    content = @Content(schema = @Schema(implementation = String.class))
            ),
            @APIResponse(
                    responseCode = "400",
                    description = "Invalid data or ontology creation error"
            )
    })
    // @RolesAllowed({"umls-create", "ontology-create"})
    @ResponseStatus(CREATED)
    Uni<String> createAllParentsGraph(
            @Parameter(
                    description = "Payload containing UMLS ID and optional source",
                    required = true
            )
            UMLSIdSourceDTO body
    );

    @GET
    @Path("/status")
    @Operation(
            summary = "Get UMLS import status",
            description = "Returns the current status of the UMLS import process (IDLE, RUNNING, FINISHED, or ERROR)."
    )
    @APIResponses({
            @APIResponse(
                    responseCode = "200",
                    description = "Current import status",
                    content = @Content(schema = @Schema(implementation = String.class))
            )
    })
    ImportStatus getImportStatus();
}
