package bio.cosy.feddb.core.services.orch.clients;

import bio.cosy.feddb.core.services.orch.dto.InfoDTO;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Response;

@Path("/docker")
@Produces("application/json")
@Consumes("application/json")
public interface DockerServiceClient {
    @GET
    @Path("info")
    InfoDTO getInfo();


    @DELETE
    @Path("workflow/{workflowId}/cleanup")
    Response cleanupWorkflow(@PathParam("workflowId") Long workflowId);

    @DELETE
    @Path("workflow/node/{workflowNodeId}/cleanup")
    Response cleanupWorkflowNode(@PathParam("workflowNodeId") Long workflowNodeId);
}
