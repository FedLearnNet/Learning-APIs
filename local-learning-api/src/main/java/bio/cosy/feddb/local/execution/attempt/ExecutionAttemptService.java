package bio.cosy.feddb.local.execution.attempt;

import bio.cosy.feddb.core.api.model.ModelSubDataDTO;
import bio.cosy.feddb.core.execution.run.dto.AppRunUploadData;
import bio.cosy.feddb.core.security.Scope;
import bio.cosy.feddb.core.security.ToolApiKeyAuthenticationMechanism;
import bio.cosy.feddb.core.security.ToolAuthenticated;
import io.quarkus.security.Authenticated;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Path("/execution/{mode}/{runId}")
@Tag(name = "Execution", description = "Uploads of the apps running on this clinic")
public interface ExecutionAttemptService {

    @POST
    @Path("credential")
    @Authenticated
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Provision a credential for an execution owned by the authenticated user")
    Response bootstrap(@PathParam("runId") Long attemptId);


    @POST
    @Path("upload/output")
    @ToolAuthenticated({Scope.EXECUTION_ATTEMPT, Scope.TOOL_RUN, Scope.LEARNING_RUN})
    @RolesAllowed(ToolApiKeyAuthenticationMechanism.ROLE)
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Operation(summary = "Uploads the outputs of an attempt")
    Response uploadOutput(@PathParam("runId") Long attemptId, @BeanParam AppRunUploadData upload);

    @POST
    @Path("upload/model")
    @ToolAuthenticated({Scope.EXECUTION_ATTEMPT, Scope.LEARNING_RUN})
    @RolesAllowed(ToolApiKeyAuthenticationMechanism.ROLE)
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Operation(summary = "Uploads the model an attempt of a training trained")
    Response uploadModel(@PathParam("runId") Long attemptId, @BeanParam ModelSubDataDTO model);
}
