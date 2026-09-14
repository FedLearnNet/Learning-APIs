package de.unihamburg.daibetes.api.notification;

import io.quarkus.security.Authenticated;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.resteasy.reactive.ResponseStatus;

import java.util.List;

@Path("/notifications")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Authenticated
@Tag(name = "Notification", description = "Service for managing user notifications")
public interface NotificationService {

    @GET
    @Operation(summary = "List notifications for the current user")
    @APIResponse(responseCode = "200", description = "List of notifications")
    List<NotificationDTO> listNotifications();

    @GET
    @Path("/{id}")
    @Operation(summary = "Get a notification by id")
    @APIResponse(responseCode = "200", description = "Notification found")
    @APIResponse(responseCode = "404", description = "Notification not found")
    NotificationDTO getNotification(@PathParam("id") Long id);

    @POST
    @Operation(summary = "Create a notification for the current user")
    @APIResponse(responseCode = "201", description = "Notification created")
    @ResponseStatus(201)
    NotificationDTO createNotification(NotificationDTO notificationDTO);

    @PUT
    @Path("/{id}")
    @Operation(summary = "Update a notification")
    @APIResponse(responseCode = "200", description = "Notification updated")
    @APIResponse(responseCode = "404", description = "Notification not found")
    NotificationDTO updateNotification(@PathParam("id") Long id, NotificationDTO notificationDTO);

    @POST
    @Path("/{id}/read")
    @Operation(summary = "Mark a notification as read")
    @APIResponse(responseCode = "200", description = "Notification marked as read")
    @APIResponse(responseCode = "404", description = "Notification not found")
    NotificationDTO markAsRead(@PathParam("id") Long id);

    @POST
    @Path("/{id}/archive")
    @Operation(summary = "Archive a notification")
    @APIResponse(responseCode = "200", description = "Notification archived")
    @APIResponse(responseCode = "404", description = "Notification not found")
    NotificationDTO archiveNotification(@PathParam("id") Long id);

    @DELETE
    @Path("/{id}")
    @Operation(summary = "Delete a notification")
    @APIResponse(responseCode = "200", description = "Notification deleted")
    @APIResponse(responseCode = "404", description = "Notification not found")
    Response deleteNotification(@PathParam("id") Long id);
}
