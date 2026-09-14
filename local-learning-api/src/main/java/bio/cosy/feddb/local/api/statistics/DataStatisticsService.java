package bio.cosy.feddb.local.api.statistics;

import bio.cosy.feddb.local.api.cohort.permission.PermissionDTO;
import io.quarkus.security.Authenticated;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Path("/datastatistics")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Authenticated
//@RolesAllowed("admin")
@Tag(name = "DataStatistics", description = "Service for managing RequestDataStatistics")
public interface DataStatisticsService {

    @GET
    @Transactional
    @Operation(summary = "List all FederatedLearningRequests",
            description = "Returns a paginated, filterable list of FederatedLearningRequests")
    @APIResponse(responseCode = "200", description = "List of FederatedLearningRequest")
    LocalDataStatisticsDTO getStatisticsForAll();

    @GET
    @Path("/cohort/{id}")
    @Operation(summary = "Retrieve a single FederatedLearningRequest", description = "Returns a single FederatedLearningRequest by its id")
    @APIResponse(responseCode = "200", description = "FederatedLearningRequest found",
            content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = PermissionDTO.class)))
    @APIResponse(responseCode = "404", description = "FederatedLearningRequest not found")
    @Transactional
    LocalDataStatisticsDTO getStatisticsForCohort(@PathParam("id") Long id);
}
