package de.unihamburg.daibetes.service;


import de.unihamburg.daibetes.api.umls.search.UMLSAtomResultDTO;
import de.unihamburg.daibetes.api.umls.search.UMLSAttributeResultDTO;
import de.unihamburg.daibetes.api.umls.search.UMLSDetailResultDTO;
import de.unihamburg.daibetes.api.umls.search.UMLSRelationshipsResultDTO;
import io.quarkus.rest.client.reactive.ClientQueryParam;
import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import java.util.List;

@RegisterRestClient(configKey = "umls-api")
@Path("/rest")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@ClientQueryParam(name = "apiKey", value = "${umls.api.key}")
//https://documentation.uts.nlm.nih.gov/rest/search/
public interface UMLSSearchService {

    @GET
    @Path("/search/current")
    Uni<UMLSResultWrapper<UMLSSearchResult>> searchConcepts(
            @QueryParam("string") String searchString,
            @QueryParam("returnIdType") @DefaultValue("concept") String returnIdType,
            @QueryParam("sabs") String sourceAbbrev,
            @QueryParam("pageSize") @DefaultValue("200") int pageSize,
            @QueryParam("pageNumber") @DefaultValue("1") int pageNumber
    );

    @GET
    @Path("/search/current")
    Uni<UMLSResultWrapper<UMLSSearchResult>> searchExactString(
            @QueryParam("string") String searchString,
            @QueryParam("searchType") @DefaultValue("exact") String searchType
    );

    @GET
    @Path("/search/current")
    Uni<UMLSResultWrapper<UMLSSearchResult>> getCodes(
            @QueryParam("string") String searchString,
            @QueryParam("returnIdType") @DefaultValue("code") String returnIdType,
            @QueryParam("sabs") @DefaultValue("SNOMEDCT_US") String sourceAbbrev
    );

    @GET
    @Path("/content/current/source/{source}/{cui}")
    Uni<UMLSResultWrapper<UMLSDetailResultDTO>> getItem(
            @PathParam("source") String source,
            @PathParam("cui") String cui
    );

    @GET
    @Path("/content/current/source/{source}/{cui}/atoms")
    Uni<UMLSResultWrapper<List<UMLSAtomResultDTO>>> getAtomNames(
            @PathParam("source") String source,
            @PathParam("cui") String cui
    );

    @GET
    @Path("/content/current/source/{source}/{cui}/parents")
    Uni<UMLSResultWrapper<List<UMLSDetailResultDTO>>> getParents(
            @PathParam("source") String source,
            @PathParam("cui") String cui,
            @QueryParam("pageSize") @DefaultValue("100") int pageSize
    );

    @GET
    @Path("/content/current/source/{source}/{cui}/attributes")
    Uni<UMLSResultWrapper<List<UMLSAttributeResultDTO>>> getAttributes(
            @PathParam("source") String source,
            @PathParam("cui") String cui,
            @QueryParam("apiKey") String apiKey
    );

    @GET
    @Path("/content/current/source/{source}/{cui}/relations")
    Uni<UMLSResultWrapper<List<UMLSRelationshipsResultDTO>>> getRelations(
            @PathParam("source") String source,
            @PathParam("cui") String cui
    );

}
