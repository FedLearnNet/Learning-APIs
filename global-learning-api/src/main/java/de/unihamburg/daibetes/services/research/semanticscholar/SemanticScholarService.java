package de.unihamburg.daibetes.services.research.semanticscholar;

import io.quarkus.rest.client.reactive.ClientQueryParam;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

//https://api.semanticscholar.org/api-docs/#tag/Paper-Data/operation/get_graph_paper_bulk_search
@RegisterRestClient(configKey = "semantic-scholar-client")
@Path("/graph/v1/paper/search")
public interface SemanticScholarService {

    @GET
    @Path("/bulk")
    @Produces(MediaType.APPLICATION_JSON)
    @ClientQueryParam(name = "fields", value = "paperId,title,abstract,authors,year,venue,externalIds,referenceCount,citationCount,isOpenAccess,externalIds,openAccessPdf,url")
    SemanticScholarResponseDTO bulkSearch(
            @HeaderParam("x-api-key") String apiKey,
            @QueryParam("query") String query,
            @QueryParam("publicationDateOrYear") String publicationDateOrYear,
            @QueryParam("token") String token,
            @QueryParam("limit") @DefaultValue("1000") int limit);

    @GET
    @Path("/bulk")
    @Produces(MediaType.APPLICATION_JSON)
    @ClientQueryParam(name = "fields", value = "paperId,title,abstract,authors,year,venue,externalIds,referenceCount,citationCount,isOpenAccess,externalIds,openAccessPdf,url")
    SemanticScholarResponseDTO bulkSearch(
            @HeaderParam("x-api-key") String apiKey,
            @QueryParam("query") String query,
            @QueryParam("token") String token,
            @QueryParam("limit") @DefaultValue("1000") int limit);

    @GET
    @Path("/bulk")
    @Produces(MediaType.APPLICATION_JSON)
    SemanticScholarResponseDTO search(
            @HeaderParam("x-api-key") String apiKey,
            @QueryParam("query") String query,
            @QueryParam("publicationDateOrYear") String publicationDateOrYear);

    @GET
    @Path("/bulk")
    @Produces(MediaType.APPLICATION_JSON)
    SemanticScholarResponseDTO search(
            @HeaderParam("x-api-key") String apiKey,
            @QueryParam("query") String query);

    @GET
    @Path("/bulk")
    @Produces(MediaType.APPLICATION_JSON)
    @ClientQueryParam(name = "fields", value = "paperId,title,abstract,authors,year,venue,externalIds,referenceCount,citationCount,isOpenAccess,openAccessPdf,url")
    SemanticScholarResponseDTO search(
            @QueryParam("query") String query,
            @QueryParam("limit") @DefaultValue("50") int limit);

    @GET
    @Path("/bulk")
    @Produces(MediaType.APPLICATION_JSON)
    @ClientQueryParam(name = "fields", value = "paperId,title,abstract,authors,year,venue,externalIds,referenceCount,citationCount,isOpenAccess,openAccessPdf,url")
    SemanticScholarResponseDTO search(
            @HeaderParam("x-api-key") String apiKey,
            @QueryParam("query") String query,
            @QueryParam("limit") @DefaultValue("50") int limit);
}
