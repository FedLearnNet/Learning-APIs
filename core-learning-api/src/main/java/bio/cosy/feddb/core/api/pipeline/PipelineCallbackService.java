package bio.cosy.feddb.core.api.pipeline;

import bio.cosy.feddb.core.api.app.AppPublishInfoDTO;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;

public interface PipelineCallbackService {

    @PUT
    @Path("/{id}/update")
    @Operation(summary = "Process pipeline status update", operationId = "processPipelineUpdate")
    void processUpdate(@Parameter(description = "Pipeline ID", required = true) @PathParam("id") Long id,
                       @QueryParam("secret") String secret,
                       @Valid PipelineStatusUpdateDTO dto);

    @PUT
    @Path("/{id}/summary")
    @Operation(summary = "Process pipeline summary update", operationId = "processSummary")
    void processSummary(@Parameter(description = "Pipeline ID", required = true) @PathParam("id") Long id,
                        @QueryParam("secret") String secret,
                        @Valid AppPublishInfoDTO dto);

    @GET
    @Path("/{id}/info")
    @Operation(summary = "Get pipeline run info", operationId = "getPipelineRunInfo")
    PipelineRunInfoDTO getPipelineRunInfo(@Parameter(description = "Pipeline ID", required = true) @PathParam("id") Long id,
                                          @QueryParam("secret") String secret);

    @GET
    @Path("/{id}/zip")
    @Produces("application/zip")
    @Operation(summary = "Files of the pipeline as zip", operationId = "getPipelineFiles")
    Response getFiles(@Parameter(description = "Pipeline ID", required = true) @PathParam("id") Long id,
                      @QueryParam("secret") String secret);

    static Response zip(Long id, byte[] zip) {
        return Response.ok(zip, "application/zip")
                .header("Content-Disposition", "attachment; filename=\"pipeline-" + id + "-files.zip\"")
                .build();
    }
}
