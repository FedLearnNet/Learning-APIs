package de.unihamburg.daibetes.api.observer;

import io.smallrye.mutiny.Multi;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.resteasy.reactive.RestStreamElementType;

import java.util.List;

@Path("/admin/observer/client")
@Produces(MediaType.APPLICATION_JSON)
//@Authenticated
@RolesAllowed({"Admin"})
@Tag(name = "Client-Observer", description = "WebSocket client observer for admin diagnostics")
public interface FLNetClientObserverService {

    @GET
    @Path("/enabled")
    @Operation(summary = "Returns whether the observer feature is enabled.")
    @APIResponse(responseCode = "200", description = "Observer enabled state")
    boolean isEnabled();

    @GET
    @Path("/clients")
    @Operation(summary = "Returns a snapshot of currently connected WebSocket client IDs.")
    @APIResponse(responseCode = "200", description = "List of active connection IDs")
    List<String> getConnectedClients();

    @GET
    @Path("/events")
    @Produces(MediaType.SERVER_SENT_EVENTS)
    @RestStreamElementType(MediaType.APPLICATION_JSON)
    @Operation(summary = "SSE stream of WebSocket observer events (connect, disconnect, messages).")
    @APIResponse(responseCode = "200", description = "Stream of ClientObserverEventDTO")
    Multi<FLNetClientObserverEventDTO> streamEvents();

    @DELETE
    @Path("/clients")
    @Operation(summary = "remove all clients from the websocket")
    @APIResponse(responseCode = "200", description = "remove all current commections")
    Response removeAllClients();
}
