package de.unihamburg.daibetes.api.store;

import bio.cosy.feddb.core.api.app.FederatedAppDetailDTO;
import bio.cosy.feddb.core.api.model.ModelDetailDTO;
import bio.cosy.feddb.core.api.store.StoreDTO;
import bio.cosy.feddb.core.api.store.graph.ToolGraphDTO;
import bio.cosy.feddb.core.api.store.graph.ToolGraphPathDTO;
import bio.cosy.feddb.core.api.workflow.WorkflowDTO;
import bio.cosy.feddb.core.base.PagedResponse;
import de.unihamburg.daibetes.api.app.FederatedAppBO;
import de.unihamburg.daibetes.api.auth.UserIdentity;
import de.unihamburg.daibetes.api.model.ModelBO;
import de.unihamburg.daibetes.api.workflow.WorkflowBO;
import io.quarkus.panache.common.Page;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.commons.lang3.StringUtils;

import java.util.List;

@ApplicationScoped
public class StoreServiceImpl implements StoreService {

    @Inject
    UserIdentity userIdentity;

    @Inject
    StoreBO storeBO;

    @Inject
    StoreGraphBO storeGraphBO;

    @Inject
    ModelBO modelBO;

    @Inject
    FederatedAppBO federatedAppBO;

    @Inject
    WorkflowBO workflowBO;

    @Override
    public PagedResponse<StoreDTO> list(String search,
                                        List<String> appTypes,
                                        List<String> privacyTechniques,
                                        Integer minRating,
                                        boolean showUncertified,
                                        boolean hideWorkflow,
                                        boolean onlyTrainedAnalysis,
                                        String sort,
                                        int page,
                                        int size) {
        String keycloakId = userIdentity.getKeycloakId();
        return storeBO.list(keycloakId, search, appTypes, privacyTechniques, minRating,
                showUncertified, hideWorkflow, onlyTrainedAnalysis, sort,
                Page.of(page, size));
    }

    @Override
    public List<StoreDTO> all() {
        return storeBO.listAllPublished();
    }

    @Override
    public ToolGraphDTO graph(List<String> appTypes, List<String> privacyTechniques, Integer minRating, boolean showUncertified) {
        String keycloakId = userIdentity.getKeycloakId();
        return storeGraphBO.createGraph(appTypes, privacyTechniques, minRating, showUncertified, keycloakId);
    }

    @Override
    public List<ToolGraphPathDTO> findPaths(List<String> appTypes, List<String> privacyTechniques, Integer minRating, boolean showUncertified, Long from, Long to, int many) {
        String keycloakId = userIdentity.getKeycloakId();
        return storeGraphBO.findPaths(appTypes, privacyTechniques, minRating, showUncertified, from, to, many, keycloakId);
    }

    @Override
    public ModelDetailDTO getModel(Long id) {
        String keycloakId = userIdentity.getKeycloakId();
        return modelBO.getById(id, keycloakId);
    }

    @Override
    public ModelDetailDTO getModelBySub(Long id) {
        String keycloakId = userIdentity.getKeycloakId();
        return modelBO.getBySubId(id, keycloakId);
    }

    @Override
    public FederatedAppDetailDTO getApp(String id) {
        String keycloakId = userIdentity.getKeycloakId();
        Object idOrSlug = idOrSlug(id);
        return this.federatedAppBO.getAppObject(idOrSlug, keycloakId);
    }

    @Override
    public List<FederatedAppDetailDTO> getApps(List<String> ids) {
        String keycloakId = userIdentity.getKeycloakId();
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        List<Object> idOrSlugs = ids.stream().map(this::idOrSlug).toList();
        return idOrSlugs.stream()
                .map(i -> this.federatedAppBO.getAppObject(i, keycloakId))
                .toList();
    }

    @Override
    public FederatedAppDetailDTO getAppByVersion(Long id) {
        String keycloakId = userIdentity.getKeycloakId();
        return this.federatedAppBO.getByVersionId(id, keycloakId);
    }

    @Override
    public WorkflowDTO getWorkflow(Long id) {
        String keycloakId = userIdentity.getKeycloakId();
        return workflowBO.get(id, keycloakId);
    }

    @Override
    public List<FederatedAppDetailDTO> getAppsByVersions(List<Long> ids) {
        String keycloakId = userIdentity.getKeycloakId();
        return ids.stream()
                .map(i -> this.federatedAppBO.getByVersionId(i, keycloakId))
                .toList();
    }

    private Object idOrSlug(String id) {
        Object idOrSlug = id;
        if (StringUtils.isNumeric(id)) {
            idOrSlug = Long.parseLong(id);
        }
        return idOrSlug;
    }
}
