package bio.cosy.feddb.local.api.importer.transformer.code;

import io.quarkus.security.Authenticated;
import io.smallrye.mutiny.Multi;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.resteasy.reactive.RestStreamElementType;

@Path("/connector/transformer/code")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Code Transformer", description = "Checks custom transformation code against a goal data type")
@Authenticated
public interface CodeTransformerService {

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
