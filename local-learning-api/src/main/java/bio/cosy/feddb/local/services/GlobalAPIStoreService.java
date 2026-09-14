package bio.cosy.feddb.local.services;

import bio.cosy.feddb.core.api.app.FederatedAppDetailDTO;
import bio.cosy.feddb.core.api.model.ModelDetailDTO;
import bio.cosy.feddb.core.api.store.StoreDTO;
import bio.cosy.feddb.core.api.workflow.WorkflowDTO;
import bio.cosy.feddb.core.base.PagedResponse;
import jakarta.ws.rs.*;
import org.eclipse.microprofile.rest.client.annotation.RegisterProvider;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import java.util.List;

@Path("/store")
@Produces("application/json")
@Consumes("application/json")
@RegisterRestClient(configKey = "global-api")
@RegisterProvider(GlobalAPIAuthRequestFilter.class)
public interface GlobalAPIStoreService {

    @GET
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
    @Path("model/{id}")
    ModelDetailDTO getModel(@PathParam("id") Long id);

    @GET
    @Path("model/sub/{id}")
    ModelDetailDTO getModelBySub(@PathParam("id") Long id);

    @GET
    @Path("app/{id}")
    FederatedAppDetailDTO getApp(@PathParam("id") String id);

    @GET
    @Path("app/version/{id}")
    FederatedAppDetailDTO getAppByVersion(@PathParam("id") Long id);


    @GET
    @Path("app/versions")
    List<FederatedAppDetailDTO> getAppsByVersions(@QueryParam("ids") List<Long> ids);

    @GET
    @Path("workflow/{id}")
    WorkflowDTO getWorkflow(@PathParam("id") Long id);
}
