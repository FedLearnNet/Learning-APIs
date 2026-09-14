package de.unihamburg.daibetes.api.app;

import bio.cosy.feddb.core.api.app.FederatedAppDTO;
import bio.cosy.feddb.core.api.app.FederatedAppDetailDTO;
import bio.cosy.feddb.core.api.app.FederatedAppTagDTO;
import bio.cosy.feddb.core.api.app.config.ToolConfigDTO;
import bio.cosy.feddb.core.base.ValidationGroups;
import de.unihamburg.daibetes.api.app.version.FederatedAppPublishDTO;
import de.unihamburg.daibetes.api.testembed.pydantic.PydanticUpdateDTO;
import de.unihamburg.daibetes.dto.URLDTO;
import io.quarkus.security.Authenticated;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.groups.ConvertGroup;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.List;


@Path("/apps")
@Produces("application/json")
@Consumes("application/json")
//@RolesAllowed("admin")
@Tag(name = "App", description = "Service for managing app operations")
public interface FederatedAppService {

    @GET
    @Operation(summary = "Lists all apps")
    @APIResponses({
            @APIResponse(responseCode = "200", description = "List of all apps")
    })
    @Transactional
    List<FederatedAppDTO> list();

    @GET
    @Path("/my")
    @Operation(summary = "Lists all apps developed by this user")
    @APIResponses({
            @APIResponse(responseCode = "200", description = "List of all apps")
    })
    @Transactional
    @Authenticated
    List<FederatedAppDetailDTO> listMyApps();

    @GET
    @Path("/my/{id}")
    @Operation(summary = "Get apps developed by this user")
    @APIResponses({
            @APIResponse(responseCode = "200", description = "List of all apps")
    })
    @Transactional
    @Authenticated
    FederatedAppDetailDTO getMyApps(@PathParam("id") Long id);

    @GET
    @Path("/{id}")
    @Operation(summary = "Retrieve a single app")
    @APIResponse(responseCode = "200", description = "FederatedAppDetailDTO found")
    @APIResponse(responseCode = "404", description = "FederatedAppDetailDTO not found")
    @Transactional
    public FederatedAppDetailDTO retrieve(@PathParam("id") String id);

    @GET
    @Path("/{id}/pydantic/{appVersionId}")
    @Operation(summary = "Retrieve for a single app PydanticUpdateDTO options")
    @APIResponse(responseCode = "200", description = "PydanticUpdateDTO")
    @APIResponse(responseCode = "404", description = "FederatedAppDetailDTO not found")
    @Transactional
    public PydanticUpdateDTO retrievePydantic(@PathParam("id") Long id, @PathParam("appVersionId") Long appVersionId);

    @POST
    @Operation(summary = "Creates a new app")
    @APIResponses({
            @APIResponse(responseCode = "201", description = "App created"),
            @APIResponse(responseCode = "400", description = "Invalid data")
    })
    @Transactional
    Response create(@RequestBody @Valid @ConvertGroup(to = ValidationGroups.Post.class) AppCreateDTO dto);

    @PUT
    @Path("/{id}")
    @Operation(summary = "Updates an app")
    @APIResponses({
            @APIResponse(responseCode = "200", description = "App updated"),
            @APIResponse(responseCode = "400", description = "Invalid data"),
            @APIResponse(responseCode = "404", description = "Site not found")
    })
    @Transactional
    @Authenticated
    FederatedAppDetailDTO update(@PathParam("id") String id, @Valid @ConvertGroup(to = ValidationGroups.Put.class) @RequestBody FederatedAppDetailDTO siteDTO);

    @POST
    @Path("/{id}/publish")
    @Operation(summary = "Publish an App")
    @APIResponses({
            @APIResponse(responseCode = "200", description = "App published"),
            @APIResponse(responseCode = "400", description = "Invalid data"),
            @APIResponse(responseCode = "404", description = "App not found")
    })
    @Transactional
    @Authenticated
    FederatedAppDetailDTO publish(@PathParam("id") Long id, @Valid @RequestBody FederatedAppPublishDTO publishDTO);

    @POST
    @Path("/{id}/export")
    @Operation(summary = "Export an App to json")
    @APIResponses({
            @APIResponse(responseCode = "200", description = "App exported"),
            @APIResponse(responseCode = "400", description = "Invalid data"),
            @APIResponse(responseCode = "404", description = "App not found")
    })
    @Transactional
    @Authenticated
    URLDTO saveAppAsJson(@PathParam("id") Long id);

    @POST
    @Path("/{id}/export/build-info")
    @Operation(summary = "Replace build information in the exported tool JSON")
    @Transactional
    @Authenticated
    URLDTO replaceBuildInfoInJson(@PathParam("id") Long id);


    @DELETE
    @Path("/{id}")
    @Operation(summary = "Deletes an app")
    @APIResponses({
            @APIResponse(responseCode = "200", description = "App deleted"),
            @APIResponse(responseCode = "404", description = "App not found")
    })
    @Transactional
    @Authenticated
    Response delete(@PathParam("id") String id);


    @PUT
    @Path("/has_auth")
    @Operation(summary = "Check if app and user has auth")
    @APIResponses({
            @APIResponse(responseCode = "200", description = "Has auth"),
            @APIResponse(responseCode = "400", description = "Invalid data"),
            @APIResponse(responseCode = "404", description = "App not found"),
            @APIResponse(responseCode = "403", description = "No auth")
    })
    @Transactional
    Response checkForUserAuth(@Valid FederatedAppAuthCheckDTO appAuthCheckDTO);

    @GET
    @Path("/tags")
    @Operation(summary = "Get all tags")
    @APIResponses({
            @APIResponse(responseCode = "200", description = "All tags")
    })
    @Transactional
    List<FederatedAppTagDTO> getTags();

    @GET
    @Path("/predefined/config")
    @Operation(summary = "Get all predefined app configs")
    @APIResponses({
            @APIResponse(responseCode = "200", description = "All predefined app configs")
    })
    List<ToolConfigDTO> getPredefinedConfig();
}
