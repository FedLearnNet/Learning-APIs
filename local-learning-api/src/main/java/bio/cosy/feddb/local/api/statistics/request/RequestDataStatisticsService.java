package bio.cosy.feddb.local.api.statistics.request;

import bio.cosy.feddb.core.base.PagedResponse;
import bio.cosy.feddb.local.api.cohort.permission.PermissionDTO;
import bio.cosy.feddb.local.api.learning.request.FederatedLearningRequestDTO;
import bio.cosy.feddb.local.api.learning.request.FederatedLearningRequestStatus;
import io.quarkus.security.Authenticated;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Path("/datastatistics/requests")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Authenticated
//@RolesAllowed("admin")
@Tag(name = "DataStatistics", description = "Service for managing RequestDataStatistics")
public interface RequestDataStatisticsService {

    @GET
    @Operation(summary = "List all FederatedLearningRequests",
            description = "Returns a paginated, filterable list of FederatedLearningRequests")
    @APIResponse(responseCode = "200", description = "List of FederatedLearningRequest")
    PagedResponse<RequestDataStatisticsDTO> list(
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("page_size") @DefaultValue("20") int size,
            @QueryParam("status") FederatedLearningRequestStatus status,
            @QueryParam("patient_id") Long patientId);

    @GET
    @Path("/{id}")
    @Operation(summary = "Retrieve a single FederatedLearningRequest", description = "Returns a single FederatedLearningRequest by its id")
    @APIResponse(responseCode = "200", description = "FederatedLearningRequest found",
            content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = PermissionDTO.class)))
    @APIResponse(responseCode = "404", description = "FederatedLearningRequest not found")
    @Transactional
    RequestDataStatisticsDTO retrieve(@PathParam("id") Long id);


    @PUT
    @Path("/{id}")
    @Operation(summary = "Update a FederatedLearningRequest", description = "Updates an existing FederatedLearningRequest record")
    @APIResponse(responseCode = "200", description = "FederatedLearningRequest updated")
    @APIResponse(responseCode = "400", description = "Invalid data")
    @APIResponse(responseCode = "404", description = "FederatedLearningRequest not found")
    @Transactional
    RequestDataStatisticsDTO update(
            @PathParam("id") Long id,
            @Valid RequestDataStatisticsDTO updateDTO);

    @DELETE
    @Path("/{id}")
    @Operation(summary = "Delete a FederatedLearningRequests", description = "Deletes a FederatedLearningRequest record by its id")
    @APIResponse(responseCode = "200", description = "FederatedLearningRequest deleted")
    @APIResponse(responseCode = "404", description = "FederatedLearningRequest not found")
    @Transactional
    Response delete(@PathParam("id") Long id);
}
