package de.unihamburg.daibetes.api.analysis.run;

import bio.cosy.feddb.core.api.model.prediction.DataAnalysisCreatePredictionDTO;
import bio.cosy.feddb.core.api.model.prediction.DataAnalysisPredictionDTO;
import bio.cosy.feddb.core.api.model.prediction.DataAnalysisRunModesEnum;
import bio.cosy.feddb.core.api.model.prediction.DataAnalysisStopPredictionDTO;
import bio.cosy.feddb.core.api.run.AppRunUploadData;
import bio.cosy.feddb.core.security.ToolAuthenticated;
import bio.cosy.feddb.core.security.Scope;
import io.quarkus.security.Authenticated;
import io.smallrye.mutiny.Multi;
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
@Authenticated
//@RolesAllowed("admin")
@Tag(name = "ModelRun", description = "Service for upload files to Model")
public interface DataAnalysisRunService {


    @POST
    @Operation(summary = "Create Data Analysis Run")
    @APIResponses({
            @APIResponse(responseCode = "200", description = "ZIP"),
            @APIResponse(responseCode = "400", description = "Invalid data")
    })
    @Transactional
    @RestStreamElementType(MediaType.APPLICATION_JSON)
    Multi<DataAnalysisPredictionDTO> createDataAnalysisRun(DataAnalysisCreatePredictionDTO data);

    @GET
    @Operation(summary = "Get Data Analysis Run")
    @APIResponses({
            @APIResponse(responseCode = "200", description = "ZIP"),
            @APIResponse(responseCode = "400", description = "Invalid data")
    })
    @Transactional
    @Path("{id}")
    @RestStreamElementType(MediaType.APPLICATION_JSON)
    Multi<DataAnalysisPredictionDTO> getDataAnalysisRun(@PathParam("id") Long workflowId);

    @PUT
    @Path("workflow/{dataAnalysisId}/{id}")
    @Operation(summary = "Stop Model")
    @APIResponses({
            @APIResponse(responseCode = "200", description = "ZIP"),
            @APIResponse(responseCode = "400", description = "Invalid data")
    })
    @Transactional
    DataAnalysisPredictionDTO stopRun(
            @PathParam("dataAnalysisId") Long dataAnalysisId, @PathParam("id") Long id, DataAnalysisStopPredictionDTO dto);

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
    @Operation(summary = "Create Model")
    @APIResponses({
            @APIResponse(responseCode = "200", description = "ZIP"),
            @APIResponse(responseCode = "400", description = "Invalid data")
    })
    @RestStreamElementType(MediaType.APPLICATION_JSON)
    Multi<DataAnalysisPredictionDTO> createDataAnalysisWorkflowRun(
            @PathParam("dataAnalysisId") Long workflowId, DataAnalysisCreatePredictionDTO data);


    @GET
    @Path("workflow/{dataAnalysisId}/{workflowId}")
    @Operation(summary = "Create Model")
    @APIResponses({
            @APIResponse(responseCode = "200", description = "ZIP"),
            @APIResponse(responseCode = "400", description = "Invalid data")
    })
    @Transactional
    @RestStreamElementType(MediaType.APPLICATION_JSON)
    Multi<DataAnalysisPredictionDTO> getDataAnalysisWorkflowRun(
            @PathParam("dataAnalysisId") Long dataAnalysisId, @PathParam("workflowId") Long workflowId);

    @POST
    @Path("/{mode}/{runId}/upload/output")
    @ToolAuthenticated({Scope.MODEL_PREDICTION_RUN, Scope.MODEL_WORKFLOW_RUN})
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
