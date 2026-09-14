package bio.cosy.feddb.local.api.learning.project;

import bio.cosy.feddb.core.api.run.message.RunMessageLogDTO;
import bio.cosy.feddb.core.base.PagedResponse;
import bio.cosy.feddb.local.api.cohort.permission.PermissionDTO;
import bio.cosy.feddb.local.api.learning.project.run.step.FederatedLearningExperimentStepDetailDTO;
import io.quarkus.security.Authenticated;
import io.smallrye.mutiny.Multi;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.resteasy.reactive.RestStreamElementType;

@Path("/learning/projects")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Authenticated
//@RolesAllowed("admin")
@Tag(name = "FederatedLearningProjects", description = "Service for managing FederatedLearningProjects")
public interface FederatedLearningProjectService {

    @GET
    @Operation(summary = "List all FederatedLearningProjects which have started training",
            description = "Returns a paginated, filterable list of FederatedLearningProjects")
    @APIResponse(responseCode = "200", description = "List of FederatedLearningProjects")
    PagedResponse<FederatedLearningProjectDTO> list(
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("page_size") @DefaultValue("20") int size);

    @GET
    @Path("/{id}")
    @Operation(summary = "Retrieve a single FederatedLearningProjects", description = "Returns a single FederatedLearningProjects by its id")
    @APIResponse(responseCode = "200", description = "FederatedLearningProjects found",
            content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = PermissionDTO.class)))
    @APIResponse(responseCode = "404", description = "FederatedLearningProjects not found")
    @Transactional
    FederatedLearningProjectDTO retrieve(@PathParam("id") Long id);

    @GET
    @Path("{id}/export")
    @Operation(summary = "Download data as used by training")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    @APIResponses({
            @APIResponse(responseCode = "200", description = "Training"),
            @APIResponse(responseCode = "404", description = "Training not found"),
            @APIResponse(responseCode = "500", description = "Download failed")
    })
    Response downloadFile(@PathParam("id") Long fileId);

    @GET
    @Path("/{id}/step/{stepId}/messages")
    @Operation(summary = "Stream Log messages")
    @APIResponse(responseCode = "200", description = "Project experiment found")
    @APIResponse(responseCode = "404", description = "Project experiment not found")
    @RestStreamElementType(MediaType.APPLICATION_JSON)
    Multi<RunMessageLogDTO> listLogMessages(@PathParam("id") Long id,
                                                 @PathParam("stepId") Long stepId);

    @GET
    @Path("/{id}/step/{stepId}")
    @Operation(summary = "Stream Log messages")
    @APIResponse(responseCode = "200", description = "Project experiment found")
    @APIResponse(responseCode = "404", description = "Project experiment not found")
    @Transactional
    FederatedLearningExperimentStepDetailDTO getStepDetailUpdates(@PathParam("id") Long id,
                                                                       @PathParam("stepId") Long stepId
    );

}
