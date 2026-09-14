package bio.cosy.feddb.local.api.importer.run.preview;

import io.quarkus.security.Authenticated;
import io.smallrye.mutiny.Multi;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.resteasy.reactive.RestStreamElementType;

@Path("/connector/transformer/app-based")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "App Based Transformer",
        description = "Runs one app-based transformation step over the preview sample and streams its progress")
@Authenticated
public interface AppTransformerPreviewService {

    @POST
    @Path("/preview/stream")
    @Produces(MediaType.SERVER_SENT_EVENTS)
    @RestStreamElementType(MediaType.APPLICATION_JSON)
    @Operation(
            summary = "Run an app-based transformation step for the preview",
            description = "Starts the step's app, runs the preview sample through it and caches the result, "
                    + "so the preview of that step and of the steps after it shows real data. "
                    + "Progress is streamed as Server-Sent Events; the closing event has finished=true."
    )
    @APIResponses({
            @APIResponse(
                    responseCode = "200",
                    description = "SSE stream of the step's progress",
                    content = @Content(
                            mediaType = MediaType.SERVER_SENT_EVENTS,
                            schema = @Schema(implementation = AppTransformerPreviewStreamDTO.class)
                    )
            ),
            @APIResponse(responseCode = "400",
                    description = "No such step, the step is not app based, or an earlier app-based step has no result yet")
    })
    Multi<AppTransformerPreviewStreamDTO> previewStep(
            @RequestBody(content = @Content(schema = @Schema(implementation = AppTransformerPreviewRequestDTO.class)))
            AppTransformerPreviewRequestDTO request
    );
}
