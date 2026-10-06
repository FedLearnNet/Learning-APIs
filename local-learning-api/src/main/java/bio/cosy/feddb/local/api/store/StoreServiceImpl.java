package bio.cosy.feddb.local.api.store;

import bio.cosy.feddb.core.api.app.FederatedAppDetailDTO;
import bio.cosy.feddb.core.api.model.ModelDetailDTO;
import bio.cosy.feddb.core.api.store.StoreDTO;
import bio.cosy.feddb.core.api.workflow.WorkflowDTO;
import bio.cosy.feddb.core.base.PagedResponse;
import bio.cosy.feddb.local.services.GlobalAPIStoreService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.ServiceUnavailableException;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.util.List;

@ApplicationScoped
public class StoreServiceImpl implements StoreService {

    static final String UNAVAILABLE_MESSAGE = "The global store is not reachable";
    @Inject
    @RestClient
    GlobalAPIStoreService globalApi;

    @Override
    public PagedResponse<StoreDTO> list(String search, List<String> appTypes, List<String> privacyTechniques, Integer minRating, boolean showUncertified, boolean hideWorkflow, boolean onlyTrainedAnalysis, String sort, int page, int size) {
        try {
            return globalApi.list(search, appTypes, privacyTechniques, minRating, showUncertified, hideWorkflow, onlyTrainedAnalysis, sort, page, size);
        } catch (ProcessingException e) {
            throw new ServiceUnavailableException(UNAVAILABLE_MESSAGE);
        }
    }

    @Override
    public ModelDetailDTO getModel(Long id) {
        try {
            return globalApi.getModel(id);
        } catch (ProcessingException e) {
            throw new ServiceUnavailableException(UNAVAILABLE_MESSAGE);
        }
    }

    @Override
    public FederatedAppDetailDTO getApp(String id) {
        try {
            return globalApi.getApp(id);
        } catch (ProcessingException e) {
            throw new ServiceUnavailableException(UNAVAILABLE_MESSAGE);
        }
    }

    @Override
    public FederatedAppDetailDTO getAppByVersion(Long id) {
        try {
            return globalApi.getAppByVersion(id);
        } catch (ProcessingException e) {
            throw new ServiceUnavailableException(UNAVAILABLE_MESSAGE);
        }
    }

    @Override
    public List<FederatedAppDetailDTO> getAppsByVersions(List<Long> ids) {
        try {
            return globalApi.getAppsByVersions(ids);
        } catch (ProcessingException e) {
            throw new ServiceUnavailableException(UNAVAILABLE_MESSAGE);
        }
    }

    @Override
    public WorkflowDTO getWorkflow(Long id) {
        try {
            return globalApi.getWorkflow(id);
        } catch (ProcessingException e) {
            throw new ServiceUnavailableException(UNAVAILABLE_MESSAGE);
        }
    }
}
