package de.unihamburg.daibetes.api.store;

import bio.cosy.feddb.core.api.app.FederatedAppDetailDTO;
import bio.cosy.feddb.core.api.store.graph.ToolGraphBuilder;
import bio.cosy.feddb.core.api.store.graph.ToolGraphDTO;
import bio.cosy.feddb.core.api.store.graph.ToolGraphPathDTO;
import bio.cosy.feddb.core.api.store.graph.ToolGraphPathService;
import de.unihamburg.daibetes.api.app.FederatedAppBO;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;

@ApplicationScoped
public class StoreGraphBO extends ToolGraphBuilder {

    @Inject
    FederatedAppBO federatedAppBO;

    @Inject
    StoreBO storeBO;

    public ToolGraphDTO createGraph(List<String> appTypes, List<String> tags, Integer minRating, boolean showUncertified, String keycloakId) {
        List<FederatedAppDetailDTO> apps = federatedAppBO.getAllDetailedForStore(keycloakId).stream().filter(app -> {
            if (!storeBO.filterAppType(app, null, null, appTypes)) return false;
            if (!storeBO.filterAppTags(app, null, tags)) return false;
            if (!storeBO.filterMinRating(app, null, minRating)) return false;
            return storeBO.filterUncertification(app, null, showUncertified);
        }).toList();
        return createGraph(apps);
    }

    public List<ToolGraphPathDTO> findPaths(List<String> appTypes,
                                            List<String> tags,
                                            Integer minRating,
                                            boolean showUncertified,
                                            Long from,
                                            Long to,
                                            int many,
                                            String keycloakId) {
        ToolGraphDTO graph = createGraph(appTypes, tags, minRating, showUncertified, keycloakId);
        ToolGraphPathService pathService = new ToolGraphPathService();
        return pathService.findShortestPaths(graph, from, to, many);
    }
}
