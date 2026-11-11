package de.unihamburg.daibetes.execution.attempt;

import bio.cosy.feddb.core.api.model.ModelSubDataDTO;
import bio.cosy.feddb.core.execution.ExecutionPurpose;
import bio.cosy.feddb.core.execution.run.dto.AppRunUploadData;
import bio.cosy.feddb.core.security.Scope;
import bio.cosy.feddb.core.security.ToolApiKeyAuthenticationMechanism;
import bio.cosy.feddb.core.security.ToolAuthenticated;
import de.unihamburg.daibetes.api.analysis.run.DataAnalysisExecutionBO;
import de.unihamburg.daibetes.api.development.run.TestRunBO;
import de.unihamburg.daibetes.api.project.training.federated.FederatedTrainingAggregatorBO;
import de.unihamburg.daibetes.api.project.training.platform.PlatformTrainingBO;
import de.unihamburg.daibetes.execution.file.ExecutionFileBO;
import io.quarkus.security.Authenticated;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.Map;

@Path("/execution/{mode}/{runId}")
@Tag(name = "Execution", description = "Uploads of the apps running on the platform")
public class ExecutionAttemptService {

    @Inject
    ExecutionAttemptLifecycle lifecycle;

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
    DataAnalysisExecutionBO executionBO;

    @Inject
    PlatformTrainingBO trainingBO;

    @Inject
    FederatedTrainingAggregatorBO aggregatorBO;

    @Inject
    ExecutionAttemptAO attemptAO;

    @Inject
    ExecutionFileBO executionFileBO;

    @Inject
    TestRunBO testRunBO;

    @Transactional
    ExecutionAttemptEntity activeAttempt(Long attemptId) {
        ExecutionAttemptEntity attempt = attemptAO.findByIdWrite(attemptId)
                .orElseThrow(() -> new NotFoundException("Execution not found"));
        if (attempt.hasEnded()) {
            throw new WebApplicationException("Execution has ended", 409);
        }
        return attempt;
    }

    @POST
    @Path("upload/output")
    @ToolAuthenticated({Scope.EXECUTION_ATTEMPT, Scope.MODEL_PREDICTION_RUN, Scope.MODEL_WORKFLOW_RUN})
    @RolesAllowed(ToolApiKeyAuthenticationMechanism.ROLE)
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Operation(summary = "Uploads the outputs of an attempt")
    @Transactional
    public Response uploadOutput(@PathParam("runId") Long attemptId, @BeanParam AppRunUploadData upload) {
        if (upload.getRunId() != null && !attemptId.equals(upload.getRunId())) {
            throw new ForbiddenException("Output belongs to another execution");
        }
        upload.setRunId(attemptId);
        ExecutionAttemptEntity attempt = activeAttempt(attemptId);
        ExecutionPurpose purpose = attempt.getStep().getRun().getPurpose();
        if (purpose == ExecutionPurpose.TEST || purpose == ExecutionPurpose.TRAINING) {
            executionFileBO.keepOutputs(attempt, upload);
        } else if (purpose == ExecutionPurpose.ANALYSIS) {
            executionBO.uploadOutputTransactional(attemptId, upload);
        } else {
            throw new ForbiddenException("Execution does not accept output uploads");
        }
        return Response.status(Response.Status.CREATED).build();
    }

    @POST
    @Path("upload/model")
    @ToolAuthenticated({Scope.EXECUTION_ATTEMPT, Scope.MODEL_WORKFLOW_RUN})
    @RolesAllowed(ToolApiKeyAuthenticationMechanism.ROLE)
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Operation(summary = "Uploads the model an attempt of a training trained")
    @Transactional
    public Response uploadModel(@PathParam("runId") Long attemptId, @BeanParam ModelSubDataDTO model) {
        if (model.getRunId() != null && !attemptId.equals(model.getRunId())) {
            throw new ForbiddenException("Model belongs to another execution");
        }
        model.setRunId(attemptId);
        ExecutionAttemptEntity attempt = activeAttempt(attemptId);
        if (attempt.getStep().getRun().getPurpose() == ExecutionPurpose.TEST) {
            testRunBO.keepModelTransactional(attempt, model);
        } else if (FederatedTrainingAggregatorBO.isAggregator(attempt)) {
            aggregatorBO.storeModel(attempt, model);
        } else if (attempt.getStep().getRun().getPurpose() == ExecutionPurpose.TRAINING) {
            trainingBO.keepModelTransactional(attemptId, model);
        } else {
            throw new ForbiddenException("Execution does not accept model uploads");
        }
        return Response.status(Response.Status.CREATED).build();
    }
}
