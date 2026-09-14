package de.unihamburg.daibetes.api.query.statistics;

import io.quarkus.security.Authenticated;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.List;
import java.util.UUID;


@Path("/query/data-statistics")
@Produces("application/json")
@Consumes("application/json")
@Authenticated
@Tag(name = "DataStatisticsResponse", description = "Service for managing DataStatisticsResponse")
interface DataStatisticsResponseService {

    @GET
    @Path("/query/{queryId}")
    @Operation(summary = "List all queries")
    @APIResponse(responseCode = "200", description = "List of data statistics")
    @Transactional
    List<DataStatisticsResponseDTO> listForQuery(@PathParam("queryId") Long queryId);

    @GET
    @Path("/request/{requestId}")
    @Operation(summary = "List all queries")
    @APIResponse(responseCode = "200", description = "List of data statistics")
    @Transactional
    List<DataStatisticsResponseDTO> listForRequest(@PathParam("requestId") UUID queryId);


}
