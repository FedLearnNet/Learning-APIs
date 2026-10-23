package de.unihamburg.daibetes.api.analysis.run;

import bio.cosy.feddb.core.execution.orchestrator.DataAnalysisCreatePredictionDTO;
import bio.cosy.feddb.core.api.model.prediction.DataAnalysisRunModesEnum;
import bio.cosy.feddb.core.execution.run.dto.AppRunUploadData;
import bio.cosy.feddb.core.security.Scope;
import bio.cosy.feddb.core.security.ToolApiKeyAuthenticationMechanism;
import bio.cosy.feddb.core.security.ToolAuthenticated;
import io.smallrye.mutiny.Multi;
import jakarta.annotation.security.RolesAllowed;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.resteasy.reactive.RestStreamElementType;

@Path("/model/run")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "ModelRun", description = "Service for upload files to Model")
public interface DataAnalysisRunService {


    // Streams follow one run, each event is the whole run; they end when the client closes them.

    @POST
    @Operation(summary = "Starts a run outside a data analysis and streams it")
    @APIResponses({
            @APIResponse(responseCode = "200", description = "Stream of the run"),
            @APIResponse(responseCode = "400", description = "Invalid data")
    })
    @RestStreamElementType(MediaType.APPLICATION_JSON)
    Multi<DataAnalysisRunDTO> createDataAnalysisRun(DataAnalysisCreatePredictionDTO data);

    @GET
    @Path("{id}")
    @Operation(summary = "Streams a run")
    @APIResponses({
            @APIResponse(responseCode = "200", description = "Stream of the run"),
            @APIResponse(responseCode = "404", description = "Run not found")
    })
    @RestStreamElementType(MediaType.APPLICATION_JSON)
    Multi<DataAnalysisRunDTO> getDataAnalysisRun(@PathParam("id") Long runId);

    @PUT
    @Path("workflow/{dataAnalysisId}/{id}")
    @Operation(summary = "Stops a run")
    @APIResponses({
            @APIResponse(responseCode = "200", description = "The stopped run"),
            @APIResponse(responseCode = "404", description = "Run not found")
    })
    @Transactional
    DataAnalysisRunDTO stopRun(@PathParam("dataAnalysisId") Long dataAnalysisId, @PathParam("id") Long runId);

    @DELETE
    @Path("workflow/{dataAnalysisId}/{id}")
    @Operation(summary = "delete Model")
    @APIResponses({
            @APIResponse(responseCode = "200", description = "Deleted"),
            @APIResponse(responseCode = "400", description = "Invalid data")
    })
    @Transactional
    Response deleteRun(
            @PathParam("dataAnalysisId") Long dataAnalysisId,
            @PathParam("id") Long id,
            @QueryParam("workflowId") Long workflowId);

    @POST
    @Path("workflow/{dataAnalysisId}")
    @Operation(summary = "Starts a run of a data analysis and streams it")
    @APIResponses({
            @APIResponse(responseCode = "200", description = "Stream of the run"),
            @APIResponse(responseCode = "400", description = "Invalid data")
    })
    @RestStreamElementType(MediaType.APPLICATION_JSON)
    Multi<DataAnalysisRunDTO> createDataAnalysisWorkflowRun(
            @PathParam("dataAnalysisId") Long dataAnalysisId, DataAnalysisCreatePredictionDTO data);

    @POST
    @Path("/{mode}/{runId}/upload/output")
    @ToolAuthenticated({Scope.MODEL_PREDICTION_RUN, Scope.MODEL_WORKFLOW_RUN})
    @RolesAllowed(ToolApiKeyAuthenticationMechanism.ROLE)
    @Operation(summary = "Create output")
    @APIResponses({
            @APIResponse(responseCode = "201", description = "created"),
            @APIResponse(responseCode = "400", description = "Invalid data")
    })
    @Transactional
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    Response uploadOutput(
            @PathParam("mode") DataAnalysisRunModesEnum mode,
            @PathParam("runId") Long pathRunId,
            @BeanParam AppRunUploadData req);


    @GET
    @Path("/{mode}/{containerId}/download")
    @Operation(summary = "Get output")
    @APIResponses({
            @APIResponse(responseCode = "200", description = "ZIP"),
            @APIResponse(responseCode = "400", description = "Invalid data")
    })
    @Transactional
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    Response downloadOutput(
            @PathParam("mode") DataAnalysisRunModesEnum mode,
            @PathParam("containerId") String containerId);

    @GET
    @Path("/workflow/all/{experimentId}/download")
    @Operation(summary = "Get output")
    @APIResponses({
            @APIResponse(responseCode = "200", description = "ZIP"),
            @APIResponse(responseCode = "400", description = "Invalid data")
    })
    @Transactional
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    Response downloadWorkflowOutput(
            @PathParam("experimentId") Long experimentId);
}
