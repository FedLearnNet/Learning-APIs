package de.unihamburg.daibetes.api.model.sub.file;

import io.quarkus.security.Authenticated;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Path("/model/sub/file")
@Produces("application/json")
@Consumes("application/json")
@Tag(name = "ModelSubFile", description = "Service for downloading and deleting model sub-version files")
@Authenticated
public interface ModelSubFileService {

    @GET
    @Path("{id}/download")
    @Operation(summary = "Download a model sub-version file by its file id")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    @APIResponses({
            @APIResponse(responseCode = "200", description = "File"),
            @APIResponse(responseCode = "404", description = "File not found"),
            @APIResponse(responseCode = "500", description = "Download failed")
    })
    Response downloadFile(@PathParam("id") Long id);

    @DELETE
    @Path("{id}")
    @Operation(summary = "Delete a model sub-version file by its file id")
    @APIResponses({
            @APIResponse(responseCode = "200", description = "File deleted successfully"),
            @APIResponse(responseCode = "404", description = "File not found"),
            @APIResponse(responseCode = "500", description = "Deletion failed")
    })
    Response deleteFile(@PathParam("id") Long id);
}
