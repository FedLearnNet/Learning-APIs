package bio.cosy.feddb.local.api.learning.project.run;

import bio.cosy.feddb.core.api.model.ModelSubDataDTO;
import bio.cosy.feddb.core.api.run.AppRunUploadData;
import bio.cosy.feddb.core.security.ToolAuthenticated;
import bio.cosy.feddb.core.security.Scope;
import io.quarkus.security.Authenticated;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.resteasy.reactive.ResponseStatus;

@Path("/learning/run")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Authenticated
//@RolesAllowed("admin")
@Tag(name = "FederatedLearningExperiment", description = "Service for upload files for local learning")
public interface FederatedLearningExperimentService {


    @POST
    @Path("{runId}/upload/output")
    @ToolAuthenticated(Scope.LEARNING_RUN)
    @Operation(summary = "Create output")
    @APIResponses({
            @APIResponse(responseCode = "201", description = "created"),
            @APIResponse(responseCode = "400", description = "Invalid data")
    })
    @Transactional
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    Response uploadOutput(
            @PathParam("runId") Long runId,
            @BeanParam AppRunUploadData req);


    @POST
    @Path("{runId}/upload/model")
    @ToolAuthenticated(Scope.LEARNING_RUN)
    @Operation(summary = "Upload model")
    @APIResponses({
            @APIResponse(responseCode = "201", description = "created"),
            @APIResponse(responseCode = "400", description = "Invalid data")
    })
    @Transactional
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @ResponseStatus(201)
    Response uploadModel(@PathParam("runId") Long appId, @BeanParam ModelSubDataDTO file);

    @GET
    @Path("{containerId}/download")
    @Operation(summary = "Get output")
    @APIResponses({
            @APIResponse(responseCode = "200", description = "ZIP"),
            @APIResponse(responseCode = "400", description = "Invalid data")
    })
    @Transactional
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    Response downloadOutput(
            @PathParam("containerId") String containerId);
}
