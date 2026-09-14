package bio.cosy.feddb.local.api.search;

import io.quarkus.security.Authenticated;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.List;

@Path("search")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Authenticated
@Tag(name = "SchemaService", description = "Service for searching generally")
public interface SearchService {
    @GET
    @Operation(summary = "Query over all relevant table and return data", description = "Returns a list of all relevant table and return data")
    @APIResponse(responseCode = "200", description = "List of all search result")
    @Transactional
    List<SearchResultDTO<?>> search(@QueryParam("q") String query, @QueryParam("limit") Integer limit);
}
