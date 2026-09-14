package bio.cosy.feddb.core.services.orch.clients;

import bio.cosy.feddb.core.services.orch.dto.*;
import io.smallrye.mutiny.Multi;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.jboss.resteasy.reactive.RestStreamElementType;

import java.util.List;

@Path("/container")
@Produces("application/json")
@Consumes("application/json")
public interface ContainerServiceClient {

    @GET
    @Path("running")
    List<ContainerDTO> list();

    @GET
    @Path("running/{id}")
    ContainerDTO get(@PathParam("id") String id);

    @GET
    @Path("running/{id}/logs/stream")
    @RestStreamElementType(MediaType.APPLICATION_JSON)
    Multi<String> getLogsStream(@PathParam("id") String id);


    @GET
    @Path("run")
    List<ContainerRunDTO> listRuns();

    @GET
    @Path("run/{id}")
    ContainerRunDTO getRun(@PathParam("id") Long id);

    @GET
    @Path("run/{id}/logs")
    List<ContainerLogDTO> getRunLogs(@PathParam("id") Long id);


    /**
     * Starts a new container with the specified configuration.
     *
     * @param createDTO Container configuration details including app and workflow information
     * @param changeUrl If true, adds WS_URL and HTTP_URL environment variables for container connectivity
     * @param path      URL path to be used when changeUrl is true
     * @param port      Port number to be used when changeUrl is true (defaults to 8080)
     * @return Response containing the created container ID and any warnings
     */
    @POST
    CreateContainerResponseDTO startContainer(StartAppDTO createDTO,
                                              @QueryParam("changeUrl") boolean changeUrl,
                                              @QueryParam("sendConsoleLog") @DefaultValue("false") boolean sendConsoleLog,
                                              @QueryParam("path") String path,
                                              @QueryParam("port") @DefaultValue("8080") Integer port);

    @POST
    @Path("pipeline")
    CreateContainerResponseDTO startPipeline(StartPipelineDTO createDTO,
                                          @QueryParam("changeUrl") boolean changeUrl,
                                          @QueryParam("path") String path,
                                          @QueryParam("port") @DefaultValue("8080") Integer port);

    @DELETE
    @Path("{containerId}")
    Response cleanupWorkflow(@PathParam("containerId") String containerId,
                             @QueryParam("cleanup") boolean cleanup);

    @GET
    @Path("fc")
    @Operation(summary = "List all FeatureCloud containers")
    @APIResponse(responseCode = "200", description = "List of FeatureCloud containers")
    List<ContainerDTO> listFeatureCloud();

    @DELETE
    @Path("fc")
    @Operation(summary = "Stop and remove multiple containers")
    @APIResponse(responseCode = "200", description = "Container cleanup initiated in background")
    Response cleanupContainers(List<String> containerIds, @QueryParam("cleanup") boolean cleanup);

}
