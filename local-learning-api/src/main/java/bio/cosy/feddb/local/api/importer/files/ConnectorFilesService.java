package bio.cosy.feddb.local.api.importer.files;

import bio.cosy.feddb.local.api.importer.files.progress.ImportEventDTO;
import bio.cosy.feddb.local.api.importer.files.progress.ImportProgressDTO;
import io.smallrye.mutiny.Multi;
import io.quarkus.security.Authenticated;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.ParameterIn;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.resteasy.reactive.RestStreamElementType;

import java.util.List;


@Path("/connectors/files")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Connector File", description = "Service for connector-related file handling")
@Authenticated
public interface ConnectorFilesService {

    @POST
    @Path("/cohorts/{cohortId}/files")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    @RestStreamElementType(MediaType.APPLICATION_JSON)
    @Operation(
            summary = "Import files for a cohort",
            description = "Stores and profiles the files, answering with what happens as it happens: an event per "
                    + "phase and per table, the last one carrying the result. Naming a connector imports the file as "
                    + "that connector's input, replacing the one it currently reads and refusing a file whose columns "
                    + "do not match. The import runs to its end whether or not the caller stays to watch."
    )
    @APIResponses({
            @APIResponse(responseCode = "200", description = "Import events until the import ends"),
            @APIResponse(responseCode = "400", description = "Invalid request"),
            @APIResponse(responseCode = "404", description = "Cohort or connector not found")
    })
    Multi<ImportEventDTO> importFilesStreaming(
            @PathParam("cohortId")
            Long cohortId,
            @BeanParam
            @Valid
            ConnectorFileUploadRequestDTO request,
            @QueryParam("connectorId")
            @Parameter(
                    description = "Import the file as this connector's input. Exactly one file is allowed, and it is "
                            + "adopted only if its columns match the file it replaces.",
                    in = ParameterIn.QUERY)
            Long connectorId,
            @QueryParam("importId")
            @Parameter(
                    description = "Client-chosen id for this import. One is made up when it is left out.",
                    in = ParameterIn.QUERY)
            String importId
    );

    @GET
    @Path("/imports/{importId}/events")
    @Produces(MediaType.SERVER_SENT_EVENTS)
    @Operation(
            summary = "Follow an import",
            description = "Live events from this point until the import ends. A finished import returns its terminal "
                    + "event. Use the import resource for the complete current state."
    )
    @APIResponse(responseCode = "200", description = "Import events until the import ends")
    Multi<ImportEventDTO> followImport(
            @PathParam("importId")
            String importId
    );

    @GET
    @Path("/imports/{importId}")
    @Operation(
            summary = "Get a running or recently finished import",
            description = "How far the import has got, and what it produced or refused once it is over."
    )
    @APIResponses({
            @APIResponse(responseCode = "200", description = "Import found"),
            @APIResponse(responseCode = "404", description = "No such import, or it has been forgotten")
    })
    ImportProgressDTO getImport(
            @PathParam("importId")
            String importId
    );

    @GET
    @Path("/cohorts/{cohortId}/imports")
    @Operation(
            summary = "Get the cohort's imports",
            description = "Those still running and those that finished recently, newest first."
    )
    @APIResponse(responseCode = "200", description = "Imports listed")
    List<ImportProgressDTO> getImports(
            @PathParam("cohortId")
            Long cohortId,
            @QueryParam("connectorId")
            @Parameter(description = "Only imports feeding this connector", in = ParameterIn.QUERY)
            Long connectorId
    );

    @GET
    @Path("/cohorts/{cohortId}/files")
    @Operation(summary = "Get upload files")
    @APIResponses({
            @APIResponse(
                    responseCode = "200",
                    description = "File info loaded successfully"
            ),
            @APIResponse(responseCode = "400", description = "Invalid request"),
            @APIResponse(responseCode = "404", description = "File not found")
    })
    List<ConnectorFilesDTO> getFiles(
            @PathParam("cohortId")
            Long cohortId
    );

    @DELETE
    @Path("/cohorts/{cohortId}/files/{fileId}")
    @Operation(summary = "Delete upload files")
    @APIResponses({
            @APIResponse(
                    responseCode = "200",
                    description = "File deleted successfully"
            ),
            @APIResponse(responseCode = "400", description = "Invalid request"),
            @APIResponse(responseCode = "404", description = "File not found")
    })
    Response deleteFile(
            @PathParam("cohortId")
            Long cohortId,
            @PathParam("fileId")
            Long fileId
    );


    @GET
    @Path("/cohorts/{cohortId}/files/{fileId}")
    @Operation(summary = "Get file column and upload info")
    @APIResponses({
            @APIResponse(
                    responseCode = "200",
                    description = "File info loaded successfully; if the file does not exist or cannot be loaded, uploadInfo is empty and fileExists is false"
            ),
            @APIResponse(responseCode = "400", description = "Invalid request")
    })
    ConnectorFilesDetailDTO getFileInfo(
            @PathParam("cohortId")
            Long cohortId,
            @PathParam("fileId")
            Long fileId
    );

    @GET
    @Path("/cohorts/{cohortId}/file")
    @Operation(summary = "Get file column and upload info")
    @APIResponses({
            @APIResponse(
                    responseCode = "200",
                    description = "File info loaded successfully; if the cohort has no file or it cannot be loaded, uploadInfo is empty and fileExists is false"
            ),
            @APIResponse(responseCode = "400", description = "Invalid request")
    })
    ConnectorFilesDetailDTO getFileInfo(
            @PathParam("cohortId")
            Long cohortId
    );

    @GET
    @Path("/cohorts/{cohortId}/files/{fileId}/download")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    @Operation(summary = "Download a cohort file")
    @APIResponses({
            @APIResponse(responseCode = "200", description = "File downloaded successfully"),
            @APIResponse(responseCode = "400", description = "Invalid request"),
            @APIResponse(responseCode = "404", description = "File not found")
    })
    Response downloadConnectorFile(
            @PathParam("cohortId")
            Long cohortId,
            @PathParam("fileId")
            Long fileId
    );
}
