package de.unihamburg.daibetes.api.project.training;

import de.unihamburg.daibetes.api.project.training.federated.CreateProjectFederatedExperimentDTO;
import de.unihamburg.daibetes.api.project.training.platform.CreateProjectLocalExperimentDTO;
import de.unihamburg.daibetes.execution.run.ExecutionRunDetailDTO;
import io.smallrye.mutiny.Multi;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.resteasy.reactive.RestStreamElementType;

import java.util.List;


@Path("/project/{id}/experiment")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Project", description = "Service for managing project operations")
public interface ProjectExperimentService {

    @GET
    @Path("federated")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Lists the trainings of the project across clinics, newest first.")
    @APIResponse(responseCode = "200", description = "Trainings of the project")
    List<ExecutionRunDetailDTO> listFederated(@PathParam("id") Long id);

    @POST
    @Path("federated")
    @Operation(summary = "Sets up a training of the project's workflow across clinics and asks them to accept it.")
    @APIResponse(responseCode = "201", description = "Training set up",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = ExecutionRunDetailDTO.class)
            ))
    @APIResponse(responseCode = "400", description = "Invalid data")
    Response createFederated(@PathParam("id") Long id, @RequestBody @Valid CreateProjectFederatedExperimentDTO createDTO);

    @GET
    @Path("federated/{runId}")
    @Operation(summary = "Follows a training across clinics: the run now and on every change after.")
    @APIResponse(responseCode = "200", description = "Training found")
    @APIResponse(responseCode = "404", description = "Training not found")
    @RestStreamElementType(MediaType.APPLICATION_JSON)
    Multi<ExecutionRunDetailDTO> retrieveFederated(@PathParam("id") Long id, @PathParam("runId") Long runId);

    @PUT
    @Path("federated/{runId}/start")
    @Operation(summary = "Starts a training enough clinics accepted.")
    @APIResponse(responseCode = "200", description = "Training started")
    @APIResponse(responseCode = "404", description = "Training not found")
    ExecutionRunDetailDTO startFederated(@PathParam("id") Long id, @PathParam("runId") Long runId);

    @PUT
    @Path("federated/{runId}/stop")
    @Operation(summary = "Stops a training across clinics.")
    @APIResponse(responseCode = "200", description = "Training stopped")
    @APIResponse(responseCode = "404", description = "Training not found")
    ExecutionRunDetailDTO stopFederated(@PathParam("id") Long id, @PathParam("runId") Long runId);

    @GET
    @Path("local")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Lists the trainings of the project on the platform, newest first.")
    @APIResponse(responseCode = "200", description = "Trainings of the project")
    List<ExecutionRunDetailDTO> listLocal(@PathParam("id") Long id);

    @POST
    @Path("local")
    @Operation(summary = "Sets up a training of the project's workflow on the platform, started later.")
    @APIResponse(responseCode = "201", description = "Training set up",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = ExecutionRunDetailDTO.class)
            ))
    @APIResponse(responseCode = "400", description = "Invalid data")
    Response createLocal(@PathParam("id") Long id, @RequestBody @Valid CreateProjectLocalExperimentDTO createDTO);

    @GET
    @Path("local/{runId}")
    @Operation(summary = "Follows a training: the run now and on every change after.")
    @APIResponse(responseCode = "200", description = "Training found")
    @APIResponse(responseCode = "404", description = "Training not found")
    @RestStreamElementType(MediaType.APPLICATION_JSON)
    Multi<ExecutionRunDetailDTO> retrieveLocal(@PathParam("id") Long id, @PathParam("runId") Long runId);

    @GET
    @Path("local/{runId}/detail")
    @Operation(summary = "Retrieves a training with the logs and metrics of its steps.")
    @APIResponse(responseCode = "200", description = "Training found")
    @APIResponse(responseCode = "404", description = "Training not found")
    ExecutionRunDetailDTO retrieveLocalDetail(@PathParam("id") Long id, @PathParam("runId") Long runId);

    @PUT
    @Path("local/{runId}/start")
    @Operation(summary = "Starts a training.")
    @APIResponse(responseCode = "200", description = "Training started")
    @APIResponse(responseCode = "404", description = "Training not found")
    ExecutionRunDetailDTO startLocal(@PathParam("id") Long id, @PathParam("runId") Long runId);

    @PUT
    @Path("local/{runId}/stop")
    @Operation(summary = "Stops a training.")
    @APIResponse(responseCode = "200", description = "Training stopped")
    @APIResponse(responseCode = "404", description = "Training not found")
    ExecutionRunDetailDTO stopLocal(@PathParam("id") Long id, @PathParam("runId") Long runId);

    @POST
    @Path("local/test")
    @Operation(summary = "Tests the project's workflow on dummy data, replacing the test before.")
    @APIResponse(responseCode = "201", description = "Test started",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = ExecutionRunDetailDTO.class)
            ))
    Response createLocalTest(@PathParam("id") Long id);

    @GET
    @Path("local/test")
    @Operation(summary = "Follows the latest test of the project's workflow.")
    @APIResponse(responseCode = "200", description = "Test found")
    @RestStreamElementType(MediaType.APPLICATION_JSON)
    Multi<ExecutionRunDetailDTO> retrieveLocalTest(@PathParam("id") Long id);
}
