package de.unihamburg.daibetes.api.store;

import bio.cosy.feddb.core.api.app.FederatedAppDetailDTO;
import bio.cosy.feddb.core.api.model.ModelDetailDTO;
import bio.cosy.feddb.core.api.store.StoreDTO;
import bio.cosy.feddb.core.api.store.graph.ToolGraphDTO;
import bio.cosy.feddb.core.api.store.graph.ToolGraphPathDTO;
import bio.cosy.feddb.core.api.workflow.WorkflowDTO;
import bio.cosy.feddb.core.base.PagedResponse;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.List;

@Path("/store")
@Produces("application/json")
@Consumes("application/json")
//@RolesAllowed("admin")
@Tag(name = "Store", description = "Service for managing store operations")
public interface StoreService {

    @GET
    @Operation(summary = "Lists all store objects")
    @APIResponses({
            @APIResponse(responseCode = "200", description = "List of all store objects")
    })
    @Transactional
    PagedResponse<StoreDTO> list(
            @QueryParam("search") String search,
            @QueryParam("appTypes") List<String> appTypes,
            @QueryParam("privacyTechniques") List<String> privacyTechniques,
            @QueryParam("minRating") Integer minRating,
            @QueryParam("showUncertified") @DefaultValue("true") boolean showUncertified,
            @QueryParam("hideWorkflow") @DefaultValue("false") boolean hideWorkflow,
            @QueryParam("onlyTrainedAnalysis") @DefaultValue("false") boolean onlyTrainedAnalysis,
            @QueryParam("sort") @DefaultValue("name:asc") String sort,

            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("size") @DefaultValue("10") int size
    );

    @GET
    @Operation(summary = "Lists all store objects ")
    @APIResponses({
            @APIResponse(responseCode = "200", description = "List of all store objects")
    })
    @Transactional
    @Path("all")
    List<StoreDTO> all();

    @GET
    @Operation(summary = "Lists all store objects")
    @APIResponses({
            @APIResponse(responseCode = "200", description = "List of all store objects")
    })
    @Transactional
    @Path("graph")
    ToolGraphDTO graph(
            @QueryParam("appTypes") List<String> appTypes,
            @QueryParam("privacyTechniques") List<String> privacyTechniques,
            @QueryParam("minRating") Integer minRating,
            @QueryParam("showUncertified") @DefaultValue("true") boolean showUncertified
    );

    @GET
    @Operation(summary = "Lists all store objects")
    @APIResponses({
            @APIResponse(responseCode = "200", description = "List of all store objects")
    })
    @Transactional
    @Path("graph/from/{from}/to/{to}/many/{many}")
    List<ToolGraphPathDTO> findPaths(
            @QueryParam("appTypes") List<String> appTypes,
            @QueryParam("privacyTechniques") List<String> privacyTechniques,
            @QueryParam("minRating") Integer minRating,
            @QueryParam("showUncertified") @DefaultValue("true") boolean showUncertified,
            @PathParam("from") Long from,
            @PathParam("to") Long to,
            @PathParam("many") int many
    );

    @GET
    @Path("model/{id}")
    @Operation(summary = "Retrieve a single model wrapped in store object")
    @APIResponse(responseCode = "200", description = "FederatedAppDetailDTO found")
    @APIResponse(responseCode = "404", description = "FederatedAppDetailDTO not found")

    @Transactional
    ModelDetailDTO getModel(@PathParam("id") Long id);

    @GET
    @Path("model/sub/{id}")
    @Operation(summary = "Retrieve a single model by subId")
    @APIResponse(responseCode = "200", description = "FederatedAppDetailDTO found")
    @APIResponse(responseCode = "404", description = "FederatedAppDetailDTO not found")
    @Transactional
    ModelDetailDTO getModelBySub(@PathParam("id") Long id);

    @GET
    @Path("app/{id}")
    @Operation(summary = "Retrieve a single app")
    @APIResponse(responseCode = "200", description = "FederatedAppDetailDTO found")
    @APIResponse(responseCode = "404", description = "FederatedAppDetailDTO not found")
    @Transactional
    FederatedAppDetailDTO getApp(@PathParam("id") String id);

    @GET
    @Path("apps")
    @Operation(summary = "Retrieve multiple apps by a list of ids")
    @APIResponses({
            @APIResponse(responseCode = "200", description = "List of FederatedAppDetailDTO")
    })
    @Transactional
    List<FederatedAppDetailDTO> getApps(@QueryParam("ids") List<String> ids);

    @GET
    @Path("app/version/{id}")
    @Operation(summary = "Retrieve a single app by version Id")
    @APIResponse(responseCode = "200", description = "FederatedAppDetailDTO found")
    @APIResponse(responseCode = "404", description = "FederatedAppDetailDTO not found")
    @Transactional
    FederatedAppDetailDTO getAppByVersion(@PathParam("id") Long id);

    @GET
    @Path("workflow/{id}")
    @Operation(summary = "Retrieve a single app by version Id")
    @APIResponse(responseCode = "200", description = "FederatedAppDetailDTO found")
    @APIResponse(responseCode = "404", description = "FederatedAppDetailDTO not found")
    @Transactional
    WorkflowDTO getWorkflow(@PathParam("id") Long id);

    @GET
    @Path("app/versions")
    @Operation(summary = "Retrieve multiple apps by version Ids")
    @APIResponse(responseCode = "200", description = "FederatedAppDetailDTO list found (may be empty)")
    @APIResponse(responseCode = "400", description = "Invalid request")
    @Transactional
    List<FederatedAppDetailDTO> getAppsByVersions(@QueryParam("ids") List<Long> ids);
}
