package bio.cosy.feddb.local.api.importer.extract;

import io.quarkus.security.Authenticated;
import io.smallrye.mutiny.Multi;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.resteasy.reactive.RestStreamElementType;

@Path("/connector/extractor/app-based")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "App Based Extractor", description = "Triggers an app based extractor and streams updates as Server-Sent Events")
@Authenticated
public interface AppBasedExtractorService {

    @POST
    @Path("/stream")
    @Produces(MediaType.SERVER_SENT_EVENTS)
    @RestStreamElementType(MediaType.APPLICATION_JSON)
    @Operation(
            summary = "App based extractor",
            description = "Triggers an app based extractor and streams updates as Server-Sent Events. Final results are persisted to the cohort file store with timestamped filenames."
    )
    @APIResponses({
            @APIResponse(
                    responseCode = "200",
                    description = "SSE stream",
                    content = @Content(
                            mediaType = MediaType.SERVER_SENT_EVENTS,
                            schema = @Schema(type = SchemaType.STRING, implementation = String.class)
                    )
            ),
            @APIResponse(
                    responseCode = "400",
                    description = "Bad request"
            )
    })
    Multi<ConnectorExtractorStreamDTO> post(
            @RequestBody(
                    content = @Content(schema = @Schema(implementation = AppBasedExtractorRequestDTO.class))
            )
            AppBasedExtractorRequestDTO request
    );
}
