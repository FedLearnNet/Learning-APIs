package de.unihamburg.daibetes.api.analysis;

import bio.cosy.feddb.core.api.file.FileRenameDTO;
import bio.cosy.feddb.core.api.model.workflow.DataAnalysisDetailDTO;
import bio.cosy.feddb.core.api.model.workflow.DataAnalysisFileDTO;
import bio.cosy.feddb.core.api.model.workflow.ModelWorkflowDTO;
import bio.cosy.feddb.core.api.run.AppRunTypeEnum;
import de.unihamburg.daibetes.api.analysis.worklfow.DataAnalysisWorkflowRunDTO;
import io.quarkus.security.Authenticated;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.resteasy.reactive.ResponseStatus;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.RestStreamElementType;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import java.util.List;


@Path("/model/workflow")
@Produces("application/json")
@Consumes("application/json")
//@RolesAllowed("admin")
@Tag(name = "Model", description = "Service for managing app operations")
@Authenticated
public interface DataAnalysisService {

    @GET
    @Operation(summary = "Lists all workflows")
    @APIResponses({
            @APIResponse(responseCode = "200", description = "List of all model workflows")
    })
    @Transactional
    List<ModelWorkflowDTO> list();

    @GET
    @Path("/{id}")
    @Operation(summary = "Retrieve a single model workflow")
    @APIResponse(responseCode = "200", description = "ModelWorkflowDetailDTO found")
    @APIResponse(responseCode = "404", description = "ModelWorkflowDetailDTO not found")
    @Transactional
    DataAnalysisDetailDTO retrieve(@PathParam("id") Long id);

    @GET
    @Path("/{id}/experiment/{experimentId}")
    @Operation(summary = "Retrieve a single model data analysis workflow experiment")
    @APIResponse(responseCode = "200", description = "ModelWorkflowDetailDTO found")
    @APIResponse(responseCode = "404", description = "ModelWorkflowDetailDTO not found")
    @Transactional
    DataAnalysisWorkflowRunDTO retrieveWorkflowExperiment(@PathParam("id") Long id, @PathParam("experimentId") Long experimentId);

    @DELETE
    @Path("/{id}")
    @Operation(summary = "Deletes a model")
    @APIResponses({
            @APIResponse(responseCode = "200", description = "model deleted"),
            @APIResponse(responseCode = "404", description = "model not found")
    })
    @Transactional
    Response delete(@PathParam("id") Long id);

    @POST
    @Operation(summary = "Creates a new ModelWorkflow")
    @APIResponse(responseCode = "201", description = "Workflow created",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = ModelWorkflowDTO.class)
            ))
    @APIResponse(responseCode = "400", description = "Invalid data")
    @Transactional
    Response createWorkflow(ModelWorkflowDTO workflow);

    @GET
    @Path("/{id}/files")
    @Operation(summary = "Lists all files for a workflow")
    @APIResponse(responseCode = "200", description = "List of all files")
    List<DataAnalysisFileDTO> listFiles(@PathParam("id") Long id);

    @POST
    @Path("/{id}/files/upload")
    @Operation(summary = "Upload a file for a workflow")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @APIResponses({
            @APIResponse(responseCode = "200", description = "File uploaded successfully"),
            @APIResponse(responseCode = "500", description = "Upload failed")
    })
    Response upload(@PathParam("id") Long id, @RestForm("file") FileUpload file);

    @POST
    @Path("/{id}/files/{fileId}/link")
    @Operation(summary = "link a file for a workflow")
    @APIResponses({
            @APIResponse(responseCode = "200", description = "File linked successfully"),
            @APIResponse(responseCode = "500", description = "linked failed")
    })
    @ResponseStatus(201)
    DataAnalysisFileDTO linkFile(@PathParam("id") Long id,@PathParam("fileId") Long fileId);


    @DELETE
    @Path("/{id}/files/{fileId}")
    @Operation(summary = "Delete a file for a workflow")
    @APIResponses({
            @APIResponse(responseCode = "204", description = "File deleted successfully"),
            @APIResponse(responseCode = "404", description = "File not found"),
            @APIResponse(responseCode = "500", description = "Deletion failed")
    })
    Response deleteFile(@PathParam("id") Long id, @PathParam("fileId") Long fileId);

    @PUT
    @Path("/{id}/files/{fileId}/rename")
    @Operation(summary = "Rename a file for a workflow")
    @APIResponses({
            @APIResponse(responseCode = "200", description = "File updated successfully"),
            @APIResponse(responseCode = "400", description = "Invalid request"),
            @APIResponse(responseCode = "404", description = "File or workflow not found"),
            @APIResponse(responseCode = "500", description = "Internal server error")
    })
    DataAnalysisFileDTO renameFile(@PathParam("id") Long id, @PathParam("fileId") Long fileId, FileRenameDTO fileRenameDTO);

    @GET
    @Path("/{id}/report")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    Response getReport(@PathParam("id") Long id);
}
