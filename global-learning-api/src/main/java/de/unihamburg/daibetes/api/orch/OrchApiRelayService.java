package de.unihamburg.daibetes.api.orch;

import bio.cosy.feddb.core.services.orch.dto.*;
import io.smallrye.mutiny.Multi;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.resteasy.reactive.RestStreamElementType;

import java.util.List;

@Path("/admin/orch/relay")
@RolesAllowed({"Admin"})
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Relay", description = "⚠️ Relay facade to internal Docker orchestration API. For documentation, refer to the original API. Only GET endpoints are available here. " +
        "Orch-API is intended for internal use and should not be exposed to external clients. " +
        "It is designed to facilitate communication with the Docker orchestration service, providing access to container, run, and volume management functionalities.")
public interface OrchApiRelayService {
    // ---- Docker ----
    @GET
    @Path("docker/info")
    @Operation(summary = "List all Queries", description = "Returns a list of all Queries records")
    InfoDTO dockerInfo();

    // ---- Running Containers ----
    @GET
    @Path("container/running")
    @Operation(summary = "List all Queries", description = "Returns a list of all Queries records")
    List<ContainerDTO> listRunning();

    @GET
    @Path("container/running/{id}")
    @Operation(summary = "List all Queries", description = "Returns a list of all Queries records")
    ContainerDTO getRunning(@PathParam("id") String id);

    @GET
    @Path("container/running/{id}/logs/stream")
    @Produces(MediaType.SERVER_SENT_EVENTS)
    @RestStreamElementType(MediaType.APPLICATION_JSON)
    @Operation(summary = "List all Queries", description = "Returns a list of all Queries records")
    Multi<String> streamLogs(@PathParam("id") String id);

    // ---- Runs ----
    @GET
    @Path("container/run")
    @Operation(summary = "List all Queries", description = "Returns a list of all Queries records")
    List<ContainerRunDTO> listRuns();

    @GET
    @Path("container/run/{id}")
    @Operation(summary = "List all Queries", description = "Returns a list of all Queries records")
    ContainerRunDTO getRun(@PathParam("id") Long id);

    @GET
    @Path("container/run/{id}/logs")
    @Operation(summary = "List all Queries", description = "Returns a list of all Queries records")
    List<ContainerLogDTO> getRunLogs(@PathParam("id") Long id);

    // ---- Volumes ----
    @GET
    @Path("volume")
    @Operation(summary = "List all Queries", description = "Returns a list of all Queries records")
    List<InspectVolumeResponseDTO> listVolumes();

    @GET
    @Path("volume/{name}")
    @Operation(summary = "List all Queries", description = "Returns a list of all Queries records")
    InspectVolumeResponseDTO getVolume(@PathParam("name") String name);

    @DELETE
    @Path("volume/{name}")
    Response removeVolume(@PathParam("name") String name);

    @GET
    @Path("container/fc")
    @Operation(summary = "List all FeatureCloud containers")
    @APIResponse(responseCode = "200", description = "List of FeatureCloud containers")
    List<ContainerDTO> listFeatureCloud();

    @DELETE
    @Path("container/fc")
    @Operation(summary = "Stop and remove multiple containers")
    @APIResponse(responseCode = "200", description = "Container cleanup initiated in background")
    Response cleanupContainers(List<String> containerIds, @QueryParam("cleanup") boolean cleanup);
}
