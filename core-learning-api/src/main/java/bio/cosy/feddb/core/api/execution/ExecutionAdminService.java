package bio.cosy.feddb.core.api.execution;

import bio.cosy.feddb.core.base.PagedResponse;
import bio.cosy.feddb.core.execution.ExecutionLocation;
import bio.cosy.feddb.core.execution.ExecutionMode;
import bio.cosy.feddb.core.execution.ExecutionPurpose;
import bio.cosy.feddb.core.execution.attempt.BaseExecutionAttemptDTO;
import bio.cosy.feddb.core.execution.run.BaseExecutionRunDTO;
import bio.cosy.feddb.core.execution.run.types.RunStatusTypes;
import bio.cosy.feddb.core.execution.step.BaseExecutionRunStepDTO;
import bio.cosy.feddb.core.security.Roles;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Path("/admin/executions")
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed(Roles.ADMIN)
@Tag(name = "Execution-Admin", description = "All execution runs with their steps and attempts for admin diagnostics")
public interface ExecutionAdminService {

    @GET
    @Operation(summary = "Lists all runs, newest first.")
    @APIResponse(responseCode = "200", description = "Page of runs")
    PagedResponse<ExecutionRunSummaryDTO> listRuns(
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("page_size") @DefaultValue("25") int pageSize,
            @QueryParam("status") RunStatusTypes status,
            @QueryParam("purpose") ExecutionPurpose purpose,
            @QueryParam("mode") ExecutionMode mode,
            @QueryParam("location") ExecutionLocation location);

    @GET
    @Path("/{runId}")
    @Operation(summary = "Returns a run with its steps and attempts.")
    @APIResponse(responseCode = "200", description = "Run")
    @APIResponse(responseCode = "404", description = "Not found")
    BaseExecutionRunDTO getRun(@PathParam("runId") Long runId);

    @GET
    @Path("/{runId}/steps/{stepId}")
    @Operation(summary = "Returns a step of a run with its attempts.")
    @APIResponse(responseCode = "200", description = "Step")
    @APIResponse(responseCode = "404", description = "Not found")
    BaseExecutionRunStepDTO getStep(@PathParam("runId") Long runId, @PathParam("stepId") Long stepId);

    @GET
    @Path("/{runId}/steps/{stepId}/attempts/{attemptId}")
    @Operation(summary = "Returns an attempt with its logs, metrics and files.")
    @APIResponse(responseCode = "200", description = "Attempt")
    @APIResponse(responseCode = "404", description = "Not found")
    BaseExecutionAttemptDTO getAttempt(@PathParam("runId") Long runId, @PathParam("stepId") Long stepId,
                                       @PathParam("attemptId") Long attemptId);
}
