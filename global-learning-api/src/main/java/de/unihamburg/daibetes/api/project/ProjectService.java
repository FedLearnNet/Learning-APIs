package de.unihamburg.daibetes.api.project;

import bio.cosy.feddb.core.api.app.config.TabularSchemaDTO;
import bio.cosy.feddb.core.api.project.ProjectDTO;
import bio.cosy.feddb.core.api.project.ProjectDetailDTO;
import io.quarkus.security.Authenticated;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import java.util.List;


@Path("/project")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Authenticated
//@RolesAllowed("admin")
@Tag(name = "Project", description = "Service for managing project operations")
public interface ProjectService {

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Lists all projects.")
    @APIResponse(responseCode = "200", description = "List of Projects")
    @Transactional
    public List<ProjectDTO> list();

    @POST
    @Operation(summary = "Creates a new Project.")
    @APIResponse(responseCode = "201", description = "Project created",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = ProjectDTO.class)
            ))
    @APIResponse(responseCode = "400", description = "Invalid data")
    @Transactional
    public Response create(@RequestBody @Valid ProjectCreateDTO createDTO);

    @GET
    @Path("/{id}")
    @Operation(summary = "Retrieves a single project.")
    @APIResponse(responseCode = "200", description = "Project found")
    @APIResponse(responseCode = "404", description = "Project not found")
    @Transactional
    public ProjectDetailDTO retrieve(@PathParam("id") Long id);

    @PUT
    @Path("/{id}")
    @Operation(summary = "Updates a project.")
    @APIResponse(responseCode = "200", description = "Project updated")
    @APIResponse(responseCode = "400", description = "Invalid data")
    @APIResponse(responseCode = "404", description = "Project not found")
    @Transactional
    public ProjectDTO update(@PathParam("id") Long id, @RequestBody @Valid ProjectDetailDTO projectDTO);

    @GET
    @Path("/{id}/export-config/schema")
    @Operation(summary = "Creates a tabular schema from a project's export configuration.")
    @APIResponse(responseCode = "200", description = "Export configuration schema created")
    @APIResponse(responseCode = "404", description = "Project not found")
    @Transactional
    TabularSchemaDTO createExportConfigSchema(@PathParam("id") Long id);

    @GET
    @Path("/{id}/export-config/test-csv")
    @Produces("text/csv")
    @Operation(summary = "Generates a test CSV from a project's export configuration.")
    @APIResponse(responseCode = "200", description = "Test CSV generated")
    @APIResponse(responseCode = "400", description = "Project export configuration is missing required features")
    @APIResponse(responseCode = "404", description = "Project not found")
    @Transactional
    public Response generateExportConfigTestCsv(
            @PathParam("id") Long id,
            @DefaultValue("25") @QueryParam("amount") Integer amount
    );

    @DELETE
    @Path("/{id}")
    @Operation(summary = "Deletes a project.")
    @APIResponse(responseCode = "200", description = "Project deleted")
    @APIResponse(responseCode = "404", description = "Project not found")
    @Transactional
    public Response delete(@PathParam("id") Long id);

    @POST
    @Path("/{id}/files/upload")
    @Operation(summary = "Upload a file for a workflow")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @APIResponses({
            @APIResponse(responseCode = "200", description = "File uploaded successfully"),
            @APIResponse(responseCode = "500", description = "Upload failed")
    })
    Response upload(@PathParam("id") Long id, @RestForm("file") FileUpload file);
}
