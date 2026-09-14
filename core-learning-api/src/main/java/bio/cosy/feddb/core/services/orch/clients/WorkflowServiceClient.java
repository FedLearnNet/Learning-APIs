package bio.cosy.feddb.core.services.orch.clients;

import bio.cosy.feddb.core.services.orch.dto.CreateContainerResponseDTO;
import bio.cosy.feddb.core.services.orch.dto.StartWorkflowNodeDTO;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;


@Path("/container/workflow")
@Produces("application/json")
@Consumes("application/json")
public interface WorkflowServiceClient {


    @POST
    CreateContainerResponseDTO startWorkflow(StartWorkflowNodeDTO createDTO,
                                             @QueryParam("changeUrl") boolean changeUrl,
                                             @QueryParam("path") String path,
                                             @QueryParam("port") @DefaultValue("8080") Integer port);


    @DELETE
    @Path("{containerId}")
    Response cleanupWorkflow(@PathParam("containerId") String containerId,
                             @QueryParam("cleanup") boolean cleanup,
                             @QueryParam("background") boolean runInBackground);

    @DELETE
    @Path("step/{appId}")
    @Operation(summary = "Delete and stop a container")
    Response cleanupWorkflowStep(@PathParam("appId") String appId,
                                 @QueryParam("cleanup") boolean cleanup,
                                 @QueryParam("background") boolean runInBackground);

}
