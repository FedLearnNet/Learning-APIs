package de.unihamburg.daibetes.execution.attempt;

import bio.cosy.feddb.core.execution.run.dto.AppRunUploadData;
import bio.cosy.feddb.core.security.Scope;
import bio.cosy.feddb.core.security.ToolApiKeyAuthenticationMechanism;
import bio.cosy.feddb.core.security.ToolAuthenticated;
import de.unihamburg.daibetes.api.analysis.run.DataAnalysisExecutionBO;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Path("/execution/{mode}/{runId}/upload")
@Tag(name = "Execution", description = "Uploads of the apps running on the platform")
public class ExecutionAttemptService {

    @Inject
    DataAnalysisExecutionBO executionBO;

    @POST
    @Path("output")
    @ToolAuthenticated({Scope.MODEL_PREDICTION_RUN, Scope.MODEL_WORKFLOW_RUN})
    @RolesAllowed(ToolApiKeyAuthenticationMechanism.ROLE)
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Operation(summary = "Uploads the outputs of an attempt")
    public Response uploadOutput(@PathParam("runId") Long attemptId, @BeanParam AppRunUploadData upload) {
        executionBO.uploadOutputTransactional(attemptId, upload);
        return Response.status(Response.Status.CREATED).build();
    }
}
