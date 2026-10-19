package de.unihamburg.daibetes.api.datamodeler;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Path(DataModelerForwardService.PATH)
@Produces(MediaType.WILDCARD)
@Consumes(MediaType.WILDCARD)
@Tag(name = "Data Modeler Forward", description = "Forwards frontend calls to the data modeler with the user's token")
public interface DataModelerForwardService {
    String PATH = "/data-modeler";

    @GET
    @Path("{path: .*}")
    @Operation(summary = "Forward a GET to the data modeler")
    Response get(@PathParam("path") String path, @Context UriInfo uriInfo, @Context HttpHeaders headers);

    @POST
    @Path("{path: .*}")
    @Operation(summary = "Forward a POST to the data modeler")
    Response post(@PathParam("path") String path, @Context UriInfo uriInfo, @Context HttpHeaders headers, byte[] body);

    @PUT
    @Path("{path: .*}")
    @Operation(summary = "Forward a PUT to the data modeler")
    Response put(@PathParam("path") String path, @Context UriInfo uriInfo, @Context HttpHeaders headers, byte[] body);

    @DELETE
    @Path("{path: .*}")
    @Operation(summary = "Forward a DELETE to the data modeler")
    Response delete(@PathParam("path") String path, @Context UriInfo uriInfo, @Context HttpHeaders headers);
}
