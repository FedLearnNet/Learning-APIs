package de.unihamburg.daibetes.api.project.experiment.federated.metrics;

import io.quarkus.security.Authenticated;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Path("/project/{projectId}/experiment/federated/{experimentId}/metrics-request")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Authenticated
@Tag(name = "RunMetricsRequest", description = "Service for requesting local run metrics from clinic participants")
public interface RunMetricsRequestService {

    @GET
    @Operation(summary = "Get the metrics request for an experiment")
    @APIResponse(responseCode = "200", description = "Metrics request found")
    @APIResponse(responseCode = "404", description = "No metrics request exists yet")
    @Transactional
    RunMetricsRequestDTO getRequest(
            @PathParam("projectId") Long projectId,
            @PathParam("experimentId") Long experimentId);

    @POST
    @Operation(summary = "Create and broadcast a metrics request to all clinic participants")
    @APIResponse(responseCode = "200", description = "Metrics request created and broadcast")
    @Transactional
    RunMetricsRequestDTO createRequest(
            @PathParam("projectId") Long projectId,
            @PathParam("experimentId") Long experimentId);

    @GET
    @Path("/evaluation")
    @Operation(summary = "Server-computed evaluation summary (federated/local comparison, convergence, per-site)")
    @APIResponse(responseCode = "200", description = "Evaluation summary computed")
    @APIResponse(responseCode = "404", description = "No metrics request exists yet")
    @Transactional
    EvaluationSummaryDTO getEvaluationSummary(
            @PathParam("projectId") Long projectId,
            @PathParam("experimentId") Long experimentId);

    @GET
    @Path("/evaluation/export.csv")
    @Produces("text/csv")
    @Operation(summary = "Download the evaluation results bundle as a sectioned CSV for the manuscript")
    @APIResponse(responseCode = "200", description = "CSV results bundle")
    @APIResponse(responseCode = "404", description = "No metrics request exists yet")
    @Transactional
    Response exportEvaluationCsv(
            @PathParam("projectId") Long projectId,
            @PathParam("experimentId") Long experimentId);
}
