package bio.cosy.feddb.local.api.store;

import bio.cosy.feddb.core.api.app.FederatedAppDetailDTO;
import bio.cosy.feddb.core.api.model.ModelDetailDTO;
import bio.cosy.feddb.core.api.store.StoreDTO;
import bio.cosy.feddb.core.api.workflow.WorkflowDTO;
import bio.cosy.feddb.core.base.PagedResponse;
import bio.cosy.feddb.local.services.GlobalAPIStoreService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.util.List;

@ApplicationScoped
public class StoreServiceImpl implements StoreService {

    @Inject
    @RestClient
    GlobalAPIStoreService globalApi;

    @Override
    public PagedResponse<StoreDTO> list(String search, List<String> appTypes, List<String> privacyTechniques, Integer minRating, boolean showUncertified, boolean hideWorkflow, boolean onlyTrainedAnalysis, String sort, int page, int size) {
        return globalApi.list(search, appTypes, privacyTechniques, minRating, showUncertified, hideWorkflow, onlyTrainedAnalysis, sort, page, size);
    }

    @Override
    public ModelDetailDTO getModel(Long id) {
        return globalApi.getModel(id);
    }

    @Override
    public FederatedAppDetailDTO getApp(String id) {
        return globalApi.getApp(id);
    }

    @Override
    public FederatedAppDetailDTO getAppByVersion(Long id) {
        return globalApi.getAppByVersion(id);
    }

    @Override
    public List<FederatedAppDetailDTO> getAppsByVersions(List<Long> ids) {
        return globalApi.getAppsByVersions(ids);
    }

    @Override
    public WorkflowDTO getWorkflow(Long id) {
        return globalApi.getWorkflow(id);
    }
}
