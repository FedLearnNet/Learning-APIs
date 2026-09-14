package de.unihamburg.daibetes.api.store;

import bio.cosy.feddb.core.api.app.FederatedAppDTO;
import bio.cosy.feddb.core.api.app.FederatedAppTagDTO;
import bio.cosy.feddb.core.api.app.FederatedAppType;
import bio.cosy.feddb.core.api.model.ModelDTO;
import bio.cosy.feddb.core.api.store.StoreDTO;
import bio.cosy.feddb.core.api.workflow.WorkflowDTO;
import bio.cosy.feddb.core.base.PagedResponse;
import de.unihamburg.daibetes.api.app.FederatedAppBO;
import de.unihamburg.daibetes.api.model.ModelBO;
import de.unihamburg.daibetes.api.workflow.WorkflowBO;
import io.quarkus.panache.common.Page;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@ApplicationScoped
public class StoreBO {
    @Inject
    FederatedAppBO federatedAppBO;

    @Inject
    ModelBO modelBO;

    @Inject
    WorkflowBO workflowBO;

    public List<StoreDTO> list(String keycloakId, boolean hideWorkflow) {
        List<FederatedAppDTO> apps = federatedAppBO.getAllForStore(keycloakId);
        List<ModelDTO> models = modelBO.getAll(keycloakId);

        List<StoreDTO> storeDTOs = new ArrayList<>(apps.stream().map(StoreDTO::new).toList());
        storeDTOs.addAll(models.stream().map(StoreDTO::new).toList());

        if (!hideWorkflow) {
            List<WorkflowDTO> workflows = workflowBO.listWorkflows(keycloakId);
            storeDTOs.addAll(workflows.stream().map(StoreDTO::new).toList());
        }
        return storeDTOs;
    }

    public List<StoreDTO> listAllPublished() {
        List<FederatedAppDTO> apps = federatedAppBO.getAll();
        List<ModelDTO> models = modelBO.getAllPublished();

        List<StoreDTO> storeDTOs = new ArrayList<>(apps.stream().map(StoreDTO::new).toList());
        storeDTOs.addAll(models.stream().map(StoreDTO::new).toList());
        return storeDTOs;
    }


    public PagedResponse<StoreDTO> list(String keycloakId, String search, List<String> appTypes, List<String> tags,
                                        Integer minRating, boolean showUncertified, boolean hideWorkflow,
                                        boolean onlyTrainedAnalysis,
                                        String sort, Page page) {
        List<StoreDTO> allItems = list(keycloakId, hideWorkflow);
        List<StoreDTO> filteredItems = allItems.stream()
                .filter(e -> {
                    FederatedAppDTO app = e.getApp();
                    ModelDTO model = e.getModel();
                    WorkflowDTO workflow = e.getWorkflow();
                    if (!filterOnlyTrainedAnalysis(app, model, workflow, onlyTrainedAnalysis)) return false;
                    if (!filterSearch(app, model, workflow, search)) return false;
                    if (!filterAppType(app, model, workflow, appTypes)) return false;
                    if (!filterAppTags(app, workflow, tags)) return false;
                    if (!filterMinRating(app, workflow, minRating)) return false;
                    return filterUncertification(app, workflow, showUncertified);
                })
                .sorted((a, b) -> {
                    Long compareA = Optional.ofNullable(a.getWorkflow())
                            .map(WorkflowDTO::getId)
                            .orElseGet(() -> a.getApp().getId());
                    Long compareB = Optional.ofNullable(b.getWorkflow())
                            .map(WorkflowDTO::getId)
                            .orElseGet(() -> b.getApp().getId());
                    int appCompare = compareA.compareTo(compareB);
                    if (appCompare != 0) return appCompare;
                    if (a.getModel() == null && b.getModel() == null) return 0;
                    if (a.getModel() == null) return -1;
                    if (b.getModel() == null) return 1;
                    return 0;
                })
                .toList();

        int size = filteredItems.size();
        List<StoreDTO> pagedList = filteredItems.stream()
                .skip((long) page.index * page.size)
                .limit(page.size)
                .collect(Collectors.toList());

        PagedResponse<StoreDTO> response = new PagedResponse<>(pagedList, page.index, page.size);
        if (size < pagedList.size()) {
            response.setPageSize(size);
        }
        response.setTotalCount(size);
        return response;
    }

    public boolean filterAppType(FederatedAppDTO app, ModelDTO model, WorkflowDTO workflow, List<String> appTypes) {
        if (appTypes == null || appTypes.isEmpty()) {
            return true;
        }
        List<String> filter = new ArrayList<>(appTypes.stream().map(String::toLowerCase).toList());
        if (model == null && filter.contains("model")) {
            return false;
        } else {
            filter.remove("model");
        }
        if (workflow == null && filter.contains("workflow")) {
            return false;
        } else {
            filter.remove("workflow");
        }
        if (filter.isEmpty()) {
            return true;
        }

        if (workflow != null) {
            return workflow.getNodes().stream().anyMatch(n -> {
                FederatedAppDTO appNode = n.getAppDetail();
                ModelDTO modelNode = n.getModelDetail();
                return filterAppType(appNode, modelNode, null, appTypes);
            });
        }
        String appType = app.getType().toString().toLowerCase();
        return filter.contains(appType);
    }

    public boolean filterOnlyTrainedAnalysis(FederatedAppDTO app, ModelDTO model, WorkflowDTO workflow, boolean filter) {
        if (!filter) {
            return true;
        }
        if (workflow != null) {
            return true;
        }
        if (model != null) {
            return true;
        }
        if (app == null) {
            return true;
        }
        return app.getType().equals(FederatedAppType.ANALYSIS);
    }

    public boolean filterMinRating(FederatedAppDTO app, WorkflowDTO workflow, Integer minRating) {
        if (minRating == null) {
            return true;
        }
        if (workflow != null) {
            return workflow.getNodes().stream().allMatch(n -> {
                FederatedAppDTO appNode = n.getAppDetail();
                return filterMinRating(appNode, null, minRating);
            });
        }
        return app.getAverage() >= minRating;
    }

    public boolean filterUncertification(FederatedAppDTO app, WorkflowDTO workflow, boolean showUncertified) {
        if (showUncertified) {
            return true;
        }
        if (workflow != null) {
            return workflow.getNodes().stream().allMatch(n -> {
                FederatedAppDTO appNode = n.getAppDetail();
                return filterUncertification(appNode, null, false);
            });
        }
        return app.getCertificationLevel() != null && app.getCertificationLevel() > 0;
    }


    public boolean filterAppTags(FederatedAppDTO app, WorkflowDTO workflowDTO, List<String> searchTags) {
        if (searchTags == null || searchTags.isEmpty()) {
            return true;
        }
        if (workflowDTO != null) {
            List<String> finalSearchTags = searchTags;
            return workflowDTO.getNodes().stream().anyMatch(n -> {
                FederatedAppDTO appNode = n.getAppDetail();
                return filterAppTags(appNode, null, finalSearchTags);
            });
        }
        List<String> tags = app.getTags().stream().map(FederatedAppTagDTO::getName).map(String::toLowerCase).toList();
        searchTags = searchTags.stream().map(String::toLowerCase).toList();
        return !tags.stream().filter(searchTags::contains).collect(Collectors.toSet()).isEmpty();
    }

    private boolean filterSearch(FederatedAppDTO app, ModelDTO model, WorkflowDTO workflow, String search) {
        if (StringUtils.isEmpty(search)) {
            return true;
        }
        String filter = search.trim().toLowerCase();
        if (app != null) {
            return filter(app.getName(), app.getLongDescription(), app.getLongDescription(), filter);
        }
        if (model != null) {
            return filter(model.getName(), model.getLongDescription(), model.getLongDescription(), filter);
        }
        if (workflow != null) {
            return filter(workflow.getName(), workflow.getDescription(), workflow.getDescription(), filter);
        }
        return false;
    }

    public boolean filter(String name, String shortDesc, String longDesc, String search) {
        if (StringUtils.isEmpty(search)) {
            return true;
        }
        String filter = search.trim().toLowerCase();
        return (name != null && name.toLowerCase().contains(filter)) ||
                (shortDesc != null && shortDesc.toLowerCase().contains(filter)) ||
                (longDesc != null && longDesc.toLowerCase().contains(filter));
    }

}
