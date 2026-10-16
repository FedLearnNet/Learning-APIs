package bio.cosy.feddb.core.services.orch.clients;

import bio.cosy.feddb.core.services.orch.dto.InfoDTO;
import bio.cosy.feddb.core.services.orch.dto.RegistryImagesDTO;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Response;

@Path("/docker")
@Produces("application/json")
@Consumes("application/json")
public interface DockerServiceClient {
    @GET
    @Path("info")
    InfoDTO getInfo();

    @GET
    @Path("registry/images")
    RegistryImagesDTO getRegistryImages();

    @GET
    @Path("image/exists")
    boolean imageExistsInRegistry(@QueryParam("name") String name);

    @GET
    @Path("image/resolve")
    @Produces("text/plain")
    String resolveToolImage(@QueryParam("slug") String slug, @QueryParam("commitHash") String commitHash);


    @DELETE
    @Path("image")
    Response removeLocalImage(@QueryParam("name") String name);

    @DELETE
    @Path("workflow/{workflowId}/cleanup")
    Response cleanupWorkflow(@PathParam("workflowId") Long workflowId);

    @DELETE
    @Path("workflow/node/{workflowNodeId}/cleanup")
    Response cleanupWorkflowNode(@PathParam("workflowNodeId") Long workflowNodeId);
}
