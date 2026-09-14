package de.unihamburg.daibetes.api.testembed;

import bio.cosy.feddb.core.api.model.ModelDTO;
import bio.cosy.feddb.core.api.run.AppMessageWrapperDTO;
import bio.cosy.feddb.core.api.run.AppRunTypeEnum;
import bio.cosy.feddb.core.api.run.AppRunUploadData;
import bio.cosy.feddb.core.api.model.ModelSubDataDTO;
import io.quarkus.security.Authenticated;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.resteasy.reactive.ResponseStatus;

@Path("/testembed/{appId}/upload")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Authenticated
//@RolesAllowed("admin")
@Tag(name = "TestEmbedUpload", description = "Service for upload files to Testembed")
public interface TestEmbedUploadService {

    @POST
    @Path("/model")
    @Operation(summary = "Upload model")
    @APIResponses({
            @APIResponse(responseCode = "201", description = "created"),
            @APIResponse(responseCode = "400", description = "Invalid data")
    })
    @Transactional
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @ResponseStatus(201)
    AppMessageWrapperDTO<ModelDTO> uploadModel(@PathParam("appId") Long appId, @BeanParam ModelSubDataDTO file);

    @POST
    @Path("/output")
    @Operation(summary = "Create output")
    @APIResponses({
            @APIResponse(responseCode = "201", description = "created"),
            @APIResponse(responseCode = "400", description = "Invalid data")
    })
    @Transactional
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    Response uploadOutput(
            @PathParam("appId") Long appId,
            @QueryParam("runType") AppRunTypeEnum runType,
            @BeanParam AppRunUploadData req);


    @GET
    @Path("/output/{runId}")
    @Operation(summary = "Get output")
    @APIResponses({
            @APIResponse(responseCode = "200", description = "ZIP"),
            @APIResponse(responseCode = "400", description = "Invalid data")
    })
    @Transactional
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    Response downloadOutput(
            @PathParam("appId") Long appId,
            @PathParam("runId") Long runId,
            @QueryParam("runType") AppRunTypeEnum runType);
}
