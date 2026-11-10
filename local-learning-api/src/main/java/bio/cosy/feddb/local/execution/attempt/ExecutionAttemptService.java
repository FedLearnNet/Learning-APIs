package bio.cosy.feddb.local.execution.attempt;

import bio.cosy.feddb.core.api.model.ModelSubDataDTO;
import bio.cosy.feddb.core.execution.ExecutionPurpose;
import bio.cosy.feddb.core.execution.run.dto.AppRunUploadData;
import bio.cosy.feddb.core.security.Scope;
import bio.cosy.feddb.core.security.ToolApiKeyAuthenticationMechanism;
import bio.cosy.feddb.core.security.ToolAuthenticated;
import bio.cosy.feddb.local.api.importer.run.ConnectorAppRunBO;
import bio.cosy.feddb.local.api.learning.project.run.FederatedLearningRunBO;
import io.quarkus.security.Authenticated;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.Map;

@Path("/execution/{mode}/{runId}")
@Tag(name = "Execution", description = "Uploads of the apps running on this clinic")
public class ExecutionAttemptService {

    @POST
    @Path("credential")
    @Authenticated
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Provision a credential for an execution owned by the authenticated user")
    public Response bootstrap(@PathParam("runId") Long attemptId) {
        return Response.ok(Map.of("apiKey", lifecycle.bootstrapCredentialTransactional(attemptId)))
                .header("Cache-Control", "no-store").build();
    }

    @Inject
    ExecutionAttemptLifecycle lifecycle;

    @Inject
    FederatedLearningRunBO federatedLearningRunBO;

    @Inject
    ConnectorAppRunBO connectorAppRunBO;

    @POST
    @Path("upload/output")
    @ToolAuthenticated({Scope.EXECUTION_ATTEMPT, Scope.TOOL_RUN, Scope.LEARNING_RUN})
    @RolesAllowed(ToolApiKeyAuthenticationMechanism.ROLE)
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Operation(summary = "Uploads the outputs of an attempt")
    public Response uploadOutput(@PathParam("runId") Long attemptId, @BeanParam AppRunUploadData upload) {
        if (upload.getRunId() != null && !attemptId.equals(upload.getRunId())
                && !(upload.getRunId() < 0 && connectorAppRunBO.isSessionAttempt(attemptId))) {
            throw new ForbiddenException("Output belongs to another execution");
        }
        if (upload.getRunId() == null) {
            upload.setRunId(attemptId);
        }
        if (lifecycle.checkIfEndedTransactional(attemptId)) {
            throw new WebApplicationException("Execution has ended", 409);
        }
        ExecutionPurpose purpose = lifecycle.purposeOfTransactional(attemptId)
                .orElseThrow(() -> new NotFoundException("Attempt " + attemptId + " not found"));
        if (purpose == ExecutionPurpose.TRAINING) {
            federatedLearningRunBO.keepOutputsTransactional(attemptId, upload.getFilesNullsafe());
        } else {
            connectorAppRunBO.uploadOutput(attemptId, upload);
        }
        return Response.status(Response.Status.CREATED).build();
    }

    @POST
    @Path("upload/model")
    @ToolAuthenticated({Scope.EXECUTION_ATTEMPT, Scope.LEARNING_RUN})
    @RolesAllowed(ToolApiKeyAuthenticationMechanism.ROLE)
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Operation(summary = "Uploads the model an attempt of a training trained")
    public Response uploadModel(@PathParam("runId") Long attemptId, @BeanParam ModelSubDataDTO model) {
        if (model.getRunId() != null && !attemptId.equals(model.getRunId())) {
            throw new ForbiddenException("Model belongs to another execution");
        }
        model.setRunId(attemptId);
        if (lifecycle.checkIfEndedTransactional(attemptId)) {
            throw new WebApplicationException("Execution has ended", 409);
        }
        if (lifecycle.purposeOfTransactional(attemptId).orElse(null) != ExecutionPurpose.TRAINING) {
            throw new ForbiddenException("Execution does not accept model uploads");
        }
        federatedLearningRunBO.uploadModelTransactional(attemptId, model);
        return Response.status(Response.Status.CREATED).build();
    }
}
