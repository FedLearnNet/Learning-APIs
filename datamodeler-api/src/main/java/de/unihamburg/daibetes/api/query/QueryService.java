package de.unihamburg.daibetes.api.query;

import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.*;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.resteasy.reactive.Separator;

import java.util.List;
import java.util.UUID;

@Path("/query")
@Produces("application/json")
@Consumes("application/json")
//@RolesAllowed("admin")
@Tag(name = "Query", description = "Service for Query generation operations")
public interface QueryService {


    @GET
    @Path("/queryable")
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
    Uni<List<QueryConfigDTO>> getQueryableNodes(
            @Parameter(
                    name = "ontology-ids",
                    description = "Comma separated ontology IDs for filter (e.g. in query)"
            )
            @Separator(",")
            @QueryParam("ontology-ids") List<UUID> ontologyIds
    );
}
