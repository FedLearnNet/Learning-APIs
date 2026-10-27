package bio.cosy.feddb.local.execution.attempt;

import bio.cosy.feddb.core.api.model.ModelSubDataDTO;
import bio.cosy.feddb.core.execution.ExecutionPurpose;
import bio.cosy.feddb.core.execution.run.dto.AppRunUploadData;
import bio.cosy.feddb.core.security.Scope;
import bio.cosy.feddb.core.security.ToolApiKeyAuthenticationMechanism;
import bio.cosy.feddb.core.security.ToolAuthenticated;
import bio.cosy.feddb.local.api.importer.run.ConnectorAppRunBO;
import bio.cosy.feddb.local.api.learning.project.run.FederatedLearningRunBO;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Path("/execution/{mode}/{runId}/upload")
@Tag(name = "Execution", description = "Uploads of the apps running on this clinic")
public class ExecutionAttemptService {

    @Inject
    ExecutionAttemptLifecycle lifecycle;

    @Inject
    FederatedLearningRunBO federatedLearningRunBO;

    @Inject
    ConnectorAppRunBO connectorAppRunBO;

    @POST
    @Path("output")
    @ToolAuthenticated({Scope.TOOL_RUN, Scope.LEARNING_RUN})
    @RolesAllowed(ToolApiKeyAuthenticationMechanism.ROLE)
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Operation(summary = "Uploads the outputs of an attempt")
    public Response uploadOutput(@PathParam("runId") Long attemptId, @BeanParam AppRunUploadData upload) {
        ExecutionPurpose purpose = lifecycle.purposeOf(attemptId)
                .orElseThrow(() -> new NotFoundException("Attempt " + attemptId + " not found"));
        if (purpose == ExecutionPurpose.TRAINING) {
            federatedLearningRunBO.keepOutputs(attemptId, upload.getFilesNullsafe());
        } else {
            connectorAppRunBO.uploadOutput(attemptId, upload);
        }
        return Response.status(Response.Status.CREATED).build();
    }

    @POST
    @Path("model")
    @ToolAuthenticated(Scope.LEARNING_RUN)
    @RolesAllowed(ToolApiKeyAuthenticationMechanism.ROLE)
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Operation(summary = "Uploads the model an attempt of a training trained")
    public Response uploadModel(@PathParam("runId") Long attemptId, @BeanParam ModelSubDataDTO model) {
        federatedLearningRunBO.uploadModel(attemptId, model);
        return Response.status(Response.Status.CREATED).build();
    }
}
