package de.unihamburg.daibetes.api.development.engine;

import de.unihamburg.daibetes.execution.run.ExecutionRunDetailDTO;
import io.smallrye.mutiny.Multi;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.List;

@Path("/runs/test-engine/{appId}")
@Produces("application/json")
@Consumes("application/json")
@Tag(name = "Test engine", description = "Built tools started by the platform for a developer's tests")
public interface TestEngineService {

    @GET
    @Operation(summary = "Lists the ENGINE runs of a tool, newest first")
    @APIResponse(responseCode = "200", description = "ENGINE runs")
    List<ExecutionRunDetailDTO> list(@PathParam("appId") Long appId);

    @POST
    @Operation(summary = "Starts the latest built version of a tool as an ENGINE run")
    @APIResponses({
            @APIResponse(responseCode = "200", description = "ENGINE run started"),
            @APIResponse(responseCode = "400", description = "No image, or an engine already runs"),
            @APIResponse(responseCode = "403", description = "Not an author of the tool")
    })
    ExecutionRunDetailDTO start(@PathParam("appId") Long appId, @Valid TestEngineCreateDTO create);

    @POST
    @Path("/{id}/stop")
    @Operation(summary = "Stops an ENGINE run, its container is removed")
    @APIResponse(responseCode = "200", description = "ENGINE run stopped")
    ExecutionRunDetailDTO stop(@PathParam("appId") Long appId, @PathParam("id") Long id);

    @GET
    @Path("/stream")
    @Produces(MediaType.SERVER_SENT_EVENTS)
    @Operation(summary = "Stream changes of the tool's ENGINE runs (SSE)")
    @APIResponse(responseCode = "200", description = "Event stream of ExecutionRunDetailDTO")
    Multi<ExecutionRunDetailDTO> stream(@PathParam("appId") Long appId);

    @GET
    @Path("/{id}/logs")
    @Operation(summary = "Console output of an ENGINE run's container")
    @APIResponse(responseCode = "200", description = "Log lines")
    List<String> logs(@PathParam("appId") Long appId, @PathParam("id") Long id);
}
