package de.unihamburg.daibetes.api.development.run;

import de.unihamburg.daibetes.execution.run.ExecutionRunDetailDTO;

import de.unihamburg.daibetes.execution.experiment.ExecutionExperimentDTO;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.List;


@Path("/runs")
@Produces("application/json")
@Consumes("application/json")
@Tag(name = "Run", description = "Service for managing run operations")
public interface RunService {

    @GET
    @Path("/test/{appId}")
    @Operation(summary = "Lists all test runs of an app, newest first")
    @APIResponses({
            @APIResponse(responseCode = "200", description = "runs found"),
            @APIResponse(responseCode = "403", description = "Not an author of the app")
    })
    List<ExecutionRunDetailDTO> listTest(@PathParam("appId") Long appId);

    @GET
    @Path("/test/{appId}/run/{id}")
    @Operation(summary = "Retrieve a test run with the logs and metrics of its attempts")
    @APIResponse(responseCode = "200", description = "run found")
    @APIResponse(responseCode = "404", description = "run not found")
    ExecutionRunDetailDTO retrieveTest(@PathParam("appId") Long appId, @PathParam("id") Long id);

    @GET
    @Path("/federated/{appId}")
    @Operation(summary = "List all federated test runs of an app, newest first")
    @APIResponse(responseCode = "200", description = "runs found")
    List<ExecutionRunDetailDTO> listFederatedRuns(@PathParam("appId") Long appId);

    @GET
    @Path("/federated/{appId}/run/{id}")
    @Operation(summary = "Retrieve a federated test run with the logs, metrics and round messages of its participants")
    @APIResponse(responseCode = "200", description = "run found")
    @APIResponse(responseCode = "404", description = "run not found")
    ExecutionRunDetailDTO retrieveFederatedRun(@PathParam("appId") Long appId, @PathParam("id") Long id);

    @GET
    @Path("/experiment/{appId}")
    @Operation(summary = "Lists all experiments of an app, newest first")
    @APIResponses({
            @APIResponse(responseCode = "200", description = "experiments found"),
            @APIResponse(responseCode = "403", description = "Not an author of the app")
    })
    List<ExecutionRunDetailDTO> listExperiment(@PathParam("appId") Long appId);

    @GET
    @Path("/experiment/{appId}/experiment/{id}")
    @Operation(summary = "Retrieve the latest run of an experiment with the logs and metrics of its trials")
    @APIResponse(responseCode = "200", description = "experiment found")
    @APIResponse(responseCode = "404", description = "experiment not found")
    ExecutionRunDetailDTO retrieveExperiment(@PathParam("appId") Long appId, @PathParam("id") Long id);

    @PUT
    @Path("/experiment/{appId}/experiment/{id}")
    @Operation(summary = "Update the name, description and charts of an experiment")
    @APIResponse(responseCode = "200", description = "experiment updated")
    @APIResponse(responseCode = "404", description = "experiment not found")
    ExecutionExperimentDTO updateExperiment(@PathParam("appId") Long appId,
                                            @PathParam("id") Long id,
                                            @RequestBody ExecutionExperimentDTO dto);

    @POST
    @Path("/experiment/{appId}/experiment")
    @Operation(summary = "Create an experiment, a test run trying every combination of the hyper parameter values")
    @APIResponse(responseCode = "201", description = "new experiment",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = ExecutionRunDetailDTO.class)
            ))
    @APIResponse(responseCode = "400", description = "Invalid data")
    @APIResponse(responseCode = "404", description = "FederatedApp not found")
    Response createExperiment(@PathParam("appId") Long appId,
                              @RequestBody @Valid CreateExperimentDTO dto);
}
