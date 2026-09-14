package de.unihamburg.daibetes.api.runs;

import bio.cosy.feddb.core.base.ValidationGroups;
import bio.cosy.feddb.core.api.run.message.RunMessageDTO;
import bio.cosy.feddb.core.api.run.message.RunMessageLogDTO;
import bio.cosy.feddb.core.api.run.message.RunMessageMetricDTO;
import de.unihamburg.daibetes.api.runs.experiment.CreateExperimentDTO;
import de.unihamburg.daibetes.api.runs.experiment.ExperimentDTO;
import de.unihamburg.daibetes.api.runs.experiment.ExperimentDetailDTO;
import de.unihamburg.daibetes.api.runs.experiment.run.ExperimentRunDTO;
import de.unihamburg.daibetes.api.runs.test.federated.participant.FederatedParticipantDTO;
import de.unihamburg.daibetes.api.runs.test.federated.message.FederatedRoundMessageDTO;
import de.unihamburg.daibetes.api.runs.test.federated.FederatedTestRunDTO;
import de.unihamburg.daibetes.api.runs.test.TestRunDTO;
import io.quarkus.security.Authenticated;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.groups.ConvertGroup;
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
//@RolesAllowed("admin")
@Authenticated
@Tag(name = "Run", description = "Service for managing run operations")
public interface RunService {

    @GET
    @Path("/test/{appId}")
    @Operation(summary = "Lists all testruns")
    @APIResponses({
            @APIResponse(responseCode = "200", description = "runs found"),
            @APIResponse(responseCode = "404", description = "App not not found")
    })
    @Transactional
    List<TestRunDTO> listTest(@PathParam("appId") Long appId);


    @GET
    @Path("/test/{appId}/run/{id}")
    @Operation(summary = "Retrieve a single app")
    @APIResponse(responseCode = "200", description = "FederatedAppDetailDTO found")
    @APIResponse(responseCode = "404", description = "FederatedAppDetailDTO not found")
    @Transactional
    TestRunDTO retrieveTest(@PathParam("appId") Long appId, @PathParam("id") Long id);


    @GET
    @Path("/test/{appId}/run/{id}/messages")
    @Operation(summary = "Retrieve a single app")
    @APIResponse(responseCode = "200", description = "FederatedAppDetailDTO found")
    @APIResponse(responseCode = "404", description = "FederatedAppDetailDTO not found")
    @Transactional
    List<RunMessageDTO> listTestMessages(@PathParam("appId") Long appId,
                                                @PathParam("id") Long id);


    @GET
    @Path("/test/{appId}/run/{id}/log")
    @Operation(summary = "Retrieve a single app")
    @APIResponse(responseCode = "200", description = "FederatedAppDetailDTO found")
    @APIResponse(responseCode = "404", description = "FederatedAppDetailDTO not found")
    @Transactional
    List<RunMessageLogDTO> listTestLogMessages(@PathParam("appId") Long appId,
                                                      @PathParam("id") Long id);

    @GET
    @Path("/test/{appId}/run/{id}/metric")
    @Operation(summary = "Retrieve a single app")
    @APIResponse(responseCode = "200", description = "FederatedAppDetailDTO found")
    @APIResponse(responseCode = "404", description = "FederatedAppDetailDTO not found")
    @Transactional
    List<RunMessageMetricDTO> listTestMetricMessages(@PathParam("appId") Long appId,

                                                            @PathParam("id") Long id);

    @GET
    @Path("/federated/{appId}")
    @Operation(summary = "List all federated test runs for an app")
    @APIResponse(responseCode = "200", description = "runs found")
    @Transactional
    List<FederatedTestRunDTO> listFederatedRuns(@PathParam("appId") Long appId);

    @GET
    @Path("/federated/{appId}/run/{id}")
    @Operation(summary = "Retrieve a single federated test run including participants and messages")
    @APIResponse(responseCode = "200", description = "run found")
    @APIResponse(responseCode = "404", description = "run not found")
    @Transactional
    FederatedTestRunDTO retrieveFederatedRun(@PathParam("appId") Long appId, @PathParam("id") Long id);

    @GET
    @Path("/federated/{appId}/run/{id}/participants")
    @Operation(summary = "List participants for a federated test run")
    @APIResponse(responseCode = "200", description = "participants found")
    @Transactional
    List<FederatedParticipantDTO> listFederatedParticipants(@PathParam("appId") Long appId,
                                                            @PathParam("id") Long id);

    @GET
    @Path("/federated/{appId}/run/{id}/messages")
    @Operation(summary = "List round messages for a federated test run")
    @APIResponse(responseCode = "200", description = "messages found")
    @Transactional
    List<FederatedRoundMessageDTO> listFederatedRoundMessages(@PathParam("appId") Long appId,
                                                              @PathParam("id") Long id);

    @GET
    @Path("/federated/{appId}/run/{runId}/participant/{id}/messages")
    @Operation(summary = "List messages of a run")
    @APIResponse(responseCode = "200", description = "FederatedAppDetailDTO found")
    @APIResponse(responseCode = "404", description = "FederatedAppDetailDTO not found")
    @Transactional
    List<RunMessageDTO> listFederatedRunMessages(@PathParam("appId") Long appId,
                                                  @PathParam("runId") Long runId,
                                                  @PathParam("id") Long id);


    @GET
    @Path("/federated/{appId}/run/{runId}/participant/{id}/log")
    @Operation(summary = "List log messages of a run")
    @APIResponse(responseCode = "200", description = "FederatedAppDetailDTO found")
    @APIResponse(responseCode = "404", description = "FederatedAppDetailDTO not found")
    @Transactional
    List<RunMessageLogDTO> listFederatedRunLogMessages(@PathParam("appId") Long appId,
                                                        @PathParam("runId") Long runId,
                                                        @PathParam("id") Long id);


    @GET
    @Path("/federated/{appId}/run/{runId}/participant/{id}/metric")
    @Operation(summary = "List metric messages of a run")
    @APIResponse(responseCode = "200", description = "FederatedAppDetailDTO found")
    @APIResponse(responseCode = "404", description = "FederatedAppDetailDTO not found")
    @Transactional
    List<RunMessageMetricDTO> listFederatedRunMetricMessages(@PathParam("appId") Long appId,
                                                              @PathParam("runId") Long runId,
                                                              @PathParam("id") Long id);
    @GET
    @Path("/experiment/{appId}")
    @Operation(summary = "Lists all experiments")
    @APIResponses({
            @APIResponse(responseCode = "200", description = "runs found"),
            @APIResponse(responseCode = "404", description = "App not not found")
    })
    @Transactional
    List<ExperimentDTO> listExperiment(@PathParam("appId") Long appId);


    @GET
    @Path("/experiment/{appId}/experiment/{id}")
    @Operation(summary = "Retrieve a single experiment")
    @APIResponse(responseCode = "200", description = "FederatedAppDetailDTO found")
    @APIResponse(responseCode = "404", description = "FederatedAppDetailDTO not found")
    @Transactional
    ExperimentDetailDTO retrieveExperiment(@PathParam("appId") Long appId, @PathParam("id") Long id);

    @PUT
    @Path("/experiment/{appId}/experiment/{id}")
    @Operation(summary = "Update a experiment")
    @APIResponse(responseCode = "200", description = "FederatedAppDetailDTO found")
    @APIResponse(responseCode = "404", description = "FederatedAppDetailDTO not found")
    @Transactional
    ExperimentDetailDTO updateExperiment(@PathParam("appId") Long appId,
                                                @PathParam("id") Long id,
                                                @RequestBody @Valid @ConvertGroup(to = ValidationGroups.Put.class) ExperimentDetailDTO dto);


    @POST
    @Path("/experiment/{appId}/experiment")
    @Operation(summary = "Create a new experiment")
    @APIResponse(responseCode = "201", description = "new Experiment",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = ExperimentDetailDTO.class)
            ))
    @APIResponse(responseCode = "400", description = "Invalid data")
    @APIResponse(responseCode = "404", description = "FederatedApp not found")
    @Transactional
    Response createExperiment(@PathParam("appId") Long appId,
                                     @RequestBody @Valid @ConvertGroup(to = ValidationGroups.Put.class) CreateExperimentDTO dto);


    @GET
    @Path("/experiment/{appId}/experiment/{id}/runs")
    @Operation(summary = "Lists all experiments")
    @APIResponses({
            @APIResponse(responseCode = "200", description = "runs found"),
            @APIResponse(responseCode = "404", description = "App not not found")
    })
    @Transactional
    List<ExperimentRunDTO> listExperimentRuns(@PathParam("appId") Long appId,
                                              @PathParam("id") Long id);

    @GET
    @Path("/experiment/{appId}/experiment/{experimentId}/run/{id}/messages")
    @Operation(summary = "List messages of a run")
    @APIResponse(responseCode = "200", description = "FederatedAppDetailDTO found")
    @APIResponse(responseCode = "404", description = "FederatedAppDetailDTO not found")
    @Transactional
    List<RunMessageDTO> listExperimentRunMessages(@PathParam("appId") Long appId,
                                                         @PathParam("experimentId") Long experimentId,
                                                         @PathParam("id") Long id);


    @GET
    @Path("/experiment/{appId}/experiment/{experimentId}/run/{id}/log")
    @Operation(summary = "List log messages of a run")
    @APIResponse(responseCode = "200", description = "FederatedAppDetailDTO found")
    @APIResponse(responseCode = "404", description = "FederatedAppDetailDTO not found")
    @Transactional
    List<RunMessageLogDTO> listExperimentRunLogMessages(@PathParam("appId") Long appId,
                                                               @PathParam("experimentId") Long experimentId,
                                                               @PathParam("id") Long id);

    @GET
    @Path("/experiment/{appId}/experiment/{experimentId}/run/{id}/metric")
    @Operation(summary = "List metric messages of a run")
    @APIResponse(responseCode = "200", description = "FederatedAppDetailDTO found")
    @APIResponse(responseCode = "404", description = "FederatedAppDetailDTO not found")
    @Transactional
    List<RunMessageMetricDTO> listExperimentRunMetricMessages(@PathParam("appId") Long appId,
                                                                     @PathParam("experimentId") Long experimentId,
                                                                     @PathParam("id") Long id);

    @GET
    @Path("/experiment/{appId}/experiment/{experimentId}/metric")
    @Operation(summary = "List all metric messages of an experiment")
    @APIResponse(responseCode = "200", description = "FederatedAppDetailDTO found")
    @APIResponse(responseCode = "404", description = "FederatedAppDetailDTO not found")
    @Transactional
    List<RunMessageMetricDTO> listExperimentMetricMessages(@PathParam("appId") Long appId,
                                                                  @PathParam("experimentId") Long experimentId);

}
