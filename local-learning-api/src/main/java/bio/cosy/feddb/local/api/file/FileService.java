package bio.cosy.feddb.local.api.file;

import bio.cosy.feddb.core.api.file.FileContentDTO;
import bio.cosy.feddb.core.api.file.FileDTO;
import bio.cosy.feddb.core.api.file.FileRenameDTO;
import bio.cosy.feddb.core.api.file.analytics.FileProfile;
import io.quarkus.security.Authenticated;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import java.util.List;

@Path("/files")
@Produces("application/json")
@Consumes("application/json")
//@RolesAllowed("admin")
@Tag(name = "File", description = "Service for managing user Files")
@Authenticated
public interface FileService {

    @GET
    @Operation(summary = "Lists all files for a user")
    @APIResponse(responseCode = "200", description = "List of all files")
    List<FileDTO> listFiles();

    @POST
    @Path("upload")
    @Operation(summary = "Upload a file for a user")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @APIResponses({
            @APIResponse(responseCode = "201", description = "File uploaded successfully"),
            @APIResponse(responseCode = "500", description = "Upload failed")
    })
    Response upload(@RestForm("file") FileUpload file);

    @DELETE
    @Path("{id}")
    @Operation(summary = "Delete a file for a user")
    @APIResponses({
            @APIResponse(responseCode = "200", description = "File deleted successfully"),
            @APIResponse(responseCode = "404", description = "File not found"),
            @APIResponse(responseCode = "500", description = "Deletion failed")
    })
    Response deleteFile(@PathParam("id") Long fileId, @QueryParam("secret") String secret);

    @PUT
    @Path("{fileId}/rename")
    @Operation(summary = "Rename a file for a user")
    @APIResponses({
            @APIResponse(responseCode = "200", description = "File updated successfully"),
            @APIResponse(responseCode = "400", description = "Invalid request"),
            @APIResponse(responseCode = "404", description = "File or workflow not found"),
            @APIResponse(responseCode = "500", description = "Internal server error")
    })
    FileDTO renameFile(@PathParam("fileId") Long fileId, FileRenameDTO fileRenameDTO, @QueryParam("secret") String secret);

    @GET
    @Path("{id}")
    @Operation(summary = "Get file for a user")
    @APIResponse(responseCode = "200", description = "Get file")
    FileDTO getFile(@PathParam("id") Long fileId, @QueryParam("secret") String secret);

    @GET
    @Path("/{id}/content")
    @Operation(summary = "Rename a file for a user")
    @APIResponses({
            @APIResponse(responseCode = "200", description = "File data successfully"),
            @APIResponse(responseCode = "404", description = "File or workflow not found"),
    })
    FileContentDTO getFileContent(@PathParam("id") Long id, @QueryParam("secret") String secret);

    @GET
    @Path("{id}/statistics")
    @Operation(summary = "Get a file statistics for a user")
    @APIResponses({
            @APIResponse(responseCode = "200", description = "File statistics successfully"),
            @APIResponse(responseCode = "404", description = "File or workflow not found"),
    })
    FileProfile getFileStatistics(@PathParam("id") Long fileId, @QueryParam("secret") String secret);


    @GET
    @Path("{id}/download")
    @Operation(summary = "Download a file by id")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    @APIResponses({
            @APIResponse(responseCode = "200", description = "File"),
            @APIResponse(responseCode = "404", description = "File not found"),
            @APIResponse(responseCode = "500", description = "Download failed")
    })
    Response downloadFile(@PathParam("id") Long fileId, @QueryParam("secret") String secret);
}
