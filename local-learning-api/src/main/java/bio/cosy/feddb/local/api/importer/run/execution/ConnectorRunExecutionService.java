package bio.cosy.feddb.local.api.importer.run.execution;

import bio.cosy.feddb.core.api.model.prediction.DataAnalysisRunModesEnum;
import bio.cosy.feddb.core.api.run.AppRunUploadData;
import bio.cosy.feddb.core.security.ToolAuthenticated;
import bio.cosy.feddb.core.security.Scope;
import io.quarkus.security.Authenticated;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Path("/connectors/run/execution")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Authenticated
@Tag(name = "ModelRun", description = "Service for upload files to Model")
public interface ConnectorRunExecutionService {

    @POST
    @Path("{runId}/upload/output")
    @ToolAuthenticated(Scope.CONNECTOR_RUN)
    @Operation(summary = "Create output")
    @APIResponses({
            @APIResponse(responseCode = "201", description = "created"),
            @APIResponse(responseCode = "400", description = "Invalid data")
    })
    @Transactional
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    Response uploadOutput(
            @PathParam("runId") Long pathRunId,
            @BeanParam AppRunUploadData req);


}
