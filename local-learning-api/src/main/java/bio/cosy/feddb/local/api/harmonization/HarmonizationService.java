package bio.cosy.feddb.local.api.harmonization;

import bio.cosy.feddb.local.agent.etl.transform.assistant.TransformerAssistantMessageDTO;
import bio.cosy.feddb.local.api.importer.files.ConnectorFilesDetailDTO;
import io.quarkus.security.Authenticated;
import io.smallrye.mutiny.Multi;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.resteasy.reactive.ResponseStatus;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.RestStreamElementType;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import java.util.List;
import java.util.Map;

@Path("/harmonizations")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Harmonization", description = "Harmonizes a source file to the columns of a reference file")
@Authenticated
public interface HarmonizationService {

    @GET
    @Operation(summary = "List the harmonizations of the user")
    List<HarmonizationDTO> list();

    @GET
    @Path("/{id}")
    @Operation(summary = "Get a harmonization")
    HarmonizationDTO get(@PathParam("id") Long id);

    @POST
    @ResponseStatus(201)
    @Operation(summary = "Create a harmonization")
    HarmonizationDTO create(HarmonizationDTO dto);

    @PUT
    @Path("/{id}")
    @Operation(summary = "Update a harmonization")
    HarmonizationDTO update(@PathParam("id") Long id, HarmonizationDTO dto);

    @DELETE
    @Path("/{id}")
    @Operation(summary = "Delete a harmonization")
    void delete(@PathParam("id") Long id);

    @GET
    @Path("/targets")
    @Operation(summary = "The columns of a reference file as targets, with data type and whether they are static")
    List<HarmonizationTargetDTO> targets(@QueryParam("fileId") Long fileId, @QueryParam("key") String key);

    @POST
    @Path("/{id}/run")
    @Operation(summary = "Harmonize the source file and save the result as new file of the user")
    HarmonizationResultDTO run(@PathParam("id") Long id);

    @POST
    @Path("/{id}/assistant")
    @Operation(summary = "Start the assistant that maps the source file onto the reference columns in the background, or follow the one still running")
    void startAssistant(@PathParam("id") Long id);

    @GET
    @Path("/{id}/assistant")
    @Produces(MediaType.SERVER_SENT_EVENTS)
    @RestStreamElementType(MediaType.APPLICATION_JSON)
    @Operation(summary = "Events of the assistant run", description = "Streams the events so far first, then every new one")
    Multi<TransformerAssistantMessageDTO> assistantEvents(@PathParam("id") Long id);

    @GET
    @Path("/{id}/assistant/running")
    @Operation(summary = "Whether the assistant of the harmonization is still running")
    boolean assistantRunning(@PathParam("id") Long id);

    @POST
    @Path("/{id}/assistant/messages")
    @Operation(summary = "Answer, instruct, stop or apply the assistant run")
    void assistantMessage(@PathParam("id") Long id, TransformerAssistantMessageDTO message);

    @POST
    @Path("/preview")
    @Operation(summary = "The first rows of the source file after the first steps transformers")
    List<Map<String, Object>> previewTransformed(@QueryParam("steps") int steps, HarmonizationDTO dto);

    @POST
    @Path("/preview/harmonized")
    @Operation(summary = "The first rows of the source file harmonized and joined like in the run, without saving them")
    HarmonizationResultDTO previewHarmonized(HarmonizationDTO dto);

    @GET
    @Path("/files/{fileId}/sample")
    @Operation(summary = "Rows drawn from the statistics of a file, a preview of its codebook without real records")
    List<Map<String, Object>> sample(@PathParam("fileId") Long fileId, @QueryParam("rows") @DefaultValue("10") int rows);

    @GET
    @Path("/files")
    @Operation(summary = "List the files of the user for harmonizations")
    List<ConnectorFilesDetailDTO> listFiles();

    @GET
    @Path("/files/{fileId}")
    @Operation(summary = "Get a file with its columns and preview rows")
    ConnectorFilesDetailDTO getFile(@PathParam("fileId") Long fileId);

    @POST
    @Path("/files")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @ResponseStatus(201)
    @Operation(summary = "Upload and profile a file for harmonizations")
    ConnectorFilesDetailDTO uploadFile(@RestForm("file") FileUpload file);

    @GET
    @Path("/files/{fileId}/download")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    @Operation(summary = "Download a file")
    Response downloadFile(@PathParam("fileId") Long fileId);
}
