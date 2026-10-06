package bio.cosy.feddb.local.api.importer.transformer.code;

import bio.cosy.feddb.local.api.importer.transformer.code.check.CodeTransformerCheckDTO;
import bio.cosy.feddb.local.api.importer.transformer.code.check.CodeTransformerCheckResultDTO;
import bio.cosy.feddb.local.api.importer.transformer.code.runner.CodeTransformerRunnerDTO;
import bio.cosy.feddb.local.api.importer.transformer.code.save.CodeTransformerSaveDTO;
import io.quarkus.security.Authenticated;
import bio.cosy.feddb.local.api.pipeline.PipelineDTO;
import io.smallrye.common.annotation.Blocking;
import io.smallrye.mutiny.Multi;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.resteasy.reactive.RestStreamElementType;

import java.util.List;

@Path("/connector/transformer/code")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Code Transformer", description = "Checks custom transformation code against a goal data type")
@Authenticated
public interface CodeTransformerService {

    @GET
    @Operation(summary = "List the custom transformers")
    List<CodeTransformerDTO> list();

    @POST
    @Operation(
            summary = "Save a custom transformer",
            description = "Checks the code again, stores the generated project and builds it into a local image"
    )
    CodeTransformerDTO save(@Valid CodeTransformerSaveDTO request);

    @GET
    @Path("/{id: \\d+}")
    @Operation(summary = "Get a custom transformer with its latest build and the connectors using it")
    CodeTransformerDTO get(@PathParam("id") Long id);

    @DELETE
    @Path("/{id: \\d+}")
    @Operation(summary = "Delete a custom transformer and its image", description = "Refused while a connector uses it")
    void delete(@PathParam("id") Long id);

    @POST
    @Path("/{id: \\d+}/build")
    @Operation(summary = "Build the image of a custom transformer again")
    CodeTransformerDTO build(@PathParam("id") Long id);

    @GET
    @Blocking
    @Path("/{id: \\d+}/build")
    @Produces(MediaType.SERVER_SENT_EVENTS)
    @RestStreamElementType(MediaType.APPLICATION_JSON)
    @Operation(summary = "Live build of a custom transformer", description = "Streams the latest build first, then every update")
    Multi<PipelineDTO> buildUpdates(@PathParam("id") Long id);

    @POST
    @Path("/check")
    @Operation(
            summary = "Check custom transformation code",
            description = "Runs the code against the given or generated data and validates every result against the goal data type"
    )
    CodeTransformerCheckResultDTO check(CodeTransformerCheckDTO request);

    @GET
    @Path("/runner")
    @Operation(summary = "Status of the code transformer runner")
    CodeTransformerRunnerDTO getRunner();

    @POST
    @Path("/runner")
    @Operation(
            summary = "Start the code transformer runner",
            description = "Starts the shared runner, or keeps the running one, and returns once it is connected"
    )
    CodeTransformerRunnerDTO startRunner();

    @DELETE
    @Path("/runner")
    @Operation(summary = "Stop the code transformer runner")
    CodeTransformerRunnerDTO stopRunner();

    @GET
    @Path("/runner/logs")
    @Produces(MediaType.SERVER_SENT_EVENTS)
    @RestStreamElementType(MediaType.APPLICATION_JSON)
    @Operation(summary = "Live log of the code transformer runner", description = "Streams the recent lines first, then new ones")
    Multi<CodeTransformerLogDTO> runnerLogs();
}
