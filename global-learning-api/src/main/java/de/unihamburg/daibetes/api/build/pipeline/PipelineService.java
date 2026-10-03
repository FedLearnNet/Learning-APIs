package de.unihamburg.daibetes.api.build.pipeline;

import bio.cosy.feddb.core.api.pipeline.PipelineCallbackService;
import io.quarkus.security.Authenticated;
import io.smallrye.mutiny.Multi;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.List;

@Path("/pipeline")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Pipeline", description = "Service for managing user Pipelines")
@Authenticated
public interface PipelineService extends PipelineCallbackService {

    @POST
    @Transactional
    @Operation(
            summary = "Create a new pipeline",
            description = "Creates a pipeline for the authenticated user from the provided specification.",
            operationId = "createPipeline"
    )
    @APIResponses({
            @APIResponse(responseCode = "201", description = "Pipeline created",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = PipelineDTO.class))),
            @APIResponse(responseCode = "400", description = "Invalid request"),
            @APIResponse(responseCode = "401", description = "Unauthorized"),
            @APIResponse(responseCode = "409", description = "Pipeline already exists or name conflict"),
            @APIResponse(responseCode = "500", description = "Server error")
    })
    PipelineDTO create(@Valid PipelineCreateDTO request);


    @POST
    @Path("start/{id}")
    @Transactional
    @Operation(
            summary = "Start a new pipeline",
            description = "Start a pipeline for the authenticated user from the provided specification.",
            operationId = "startPipeline"
    )
    PipelineDTO start(@Parameter(description = "Pipeline ID", required = true)
                      @PathParam("id") Long id);

    @GET
    @Operation(
            summary = "List pipelines",
            description = "Returns all pipelines belonging to the authenticated user.",
            operationId = "listPipelines"
    )
    @APIResponses({
            @APIResponse(responseCode = "200", description = "List of pipelines",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(type = SchemaType.ARRAY, implementation = PipelineDTO.class))),
            @APIResponse(responseCode = "401", description = "Unauthorized")
    })
    List<PipelineDTO> list(@QueryParam("appVersionId") Long appVersionId,
                           @QueryParam("appId") Long appId,
                           @QueryParam("modelSubId") Long modelSubId);

    @GET
    @Path("/{id}")
    @Operation(
            summary = "Get a pipeline",
            description = "Fetch a single pipeline by its identifier.",
            operationId = "getPipeline"
    )
    @APIResponses({
            @APIResponse(responseCode = "200", description = "Pipeline",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = PipelineDTO.class))),
            @APIResponse(responseCode = "401", description = "Unauthorized"),
            @APIResponse(responseCode = "404", description = "Pipeline not found")
    })
    PipelineDTO get(
            @Parameter(description = "Pipeline ID", required = true)
            @PathParam("id") Long id
    );


    @PUT
    @Path("/{id}/stop")
    @Operation(
            summary = "stop a pipeline",
            description = "stop a single pipeline by its identifier.",
            operationId = "stopPipeline"
    )
    @APIResponses({
            @APIResponse(responseCode = "200", description = "Pipeline",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = PipelineDTO.class))),
            @APIResponse(responseCode = "401", description = "Unauthorized"),
            @APIResponse(responseCode = "404", description = "Pipeline not found")
    })
    PipelineDTO stopPipeline(
            @Parameter(description = "Pipeline ID", required = true)
            @PathParam("id") Long id
    );

    @GET
    @Path("/{id}/stream")
    @Produces(MediaType.SERVER_SENT_EVENTS)
    @Operation(
            summary = "Stream pipeline updates (SSE)",
            description = "Server-Sent Events stream of pipeline updates for live UI updates.",
            operationId = "streamPipeline"
    )
    @APIResponses({
            @APIResponse(responseCode = "200", description = "Event stream of PipelineDTO",
                    content = @Content(
                            mediaType = "text/event-stream",
                            schema = @Schema(implementation = PipelineDTO.class)
                    )),
            @APIResponse(responseCode = "401", description = "Unauthorized"),
            @APIResponse(responseCode = "404", description = "Pipeline not found")
    })
    Multi<PipelineDTO> stream(
            @Parameter(description = "Pipeline ID", required = true)
            @PathParam("id") Long id
    );
}
