package bio.cosy.feddb.local.api.learning.request.metrics;

import bio.cosy.feddb.core.base.PagedResponse;
import bio.cosy.feddb.local.api.learning.request.FederatedLearningRequestStatus;
import io.quarkus.security.Authenticated;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Path("/metrics/requests")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Authenticated
@Tag(name = "RunMetricsRequest", description = "Service for managing local run-metrics requests from the global coordinator")
public interface RequestRunMetricsService {

    @GET
    @Operation(summary = "List all run-metrics requests")
    @APIResponse(responseCode = "200", description = "Paginated list of run-metrics requests")
    PagedResponse<RequestRunMetricsDTO> list(
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("page_size") @DefaultValue("20") int size,
            @QueryParam("status") FederatedLearningRequestStatus status);

    @GET
    @Path("/{id}")
    @Operation(summary = "Retrieve a single run-metrics request")
    @APIResponse(responseCode = "200", description = "Run-metrics request found")
    @APIResponse(responseCode = "404", description = "Not found")
    @Transactional
    RequestRunMetricsDTO retrieve(@PathParam("id") Long id);

    @PUT
    @Path("/{id}")
    @Operation(summary = "Accept or reject a run-metrics request")
    @APIResponse(responseCode = "200", description = "Request updated")
    @APIResponse(responseCode = "404", description = "Not found")
    @Transactional
    RequestRunMetricsDTO update(
            @PathParam("id") Long id,
            @Valid RequestRunMetricsDTO updateDTO);
}
