package bio.cosy.feddb.local.api.query;

import bio.cosy.feddb.core.api.query.QueryRejectedException;
import bio.cosy.feddb.core.api.query.item.QueryItemDTO;
import bio.cosy.feddb.local.config.FLNetClientConfig;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.List;

/**
 * Rejects too large queries before they run, so run time cannot reveal single patients.
 */
@ApplicationScoped
public class QueryCostGuardBO {

    @Inject
    FLNetClientConfig config;

    /**
     * Rejects trees with too many conditions or temporal relations, or nested too deep.
     */
    public void checkShape(QueryItemDTO root) {
        if (root == null) {
            return;
        }
        FLNetClientConfig.QueryPrivacyConfig limits = config.privacy().query();
        if (root.leaves().size() > limits.maxConditions()) {
            throw new QueryRejectedException("Query has more than " + limits.maxConditions() + " conditions");
        }
        if (countTemporalRelations(root) > limits.maxTemporalRelations()) {
            throw new QueryRejectedException("Query has more than " + limits.maxTemporalRelations() + " temporal relations");
        }
        if (depthOf(root) > limits.maxDepth()) {
            throw new QueryRejectedException("Query is nested deeper than " + limits.maxDepth() + " levels");
        }
    }


    private int countTemporalRelations(QueryItemDTO node) {
        int count = 0;
        if (node.getTemporalRelations() != null) {
            count = node.getTemporalRelations().size();
        }
        for (QueryItemDTO child : childrenOf(node)) {
            count += countTemporalRelations(child);
        }
        return count;
    }

    // a leaf is one level
    private int depthOf(QueryItemDTO node) {
        int deepestChild = 0;
        for (QueryItemDTO child : childrenOf(node)) {
            deepestChild = Math.max(deepestChild, depthOf(child));
        }
        return 1 + deepestChild;
    }

    // runs before validation, so conditions may still be missing or null
    private List<QueryItemDTO> childrenOf(QueryItemDTO node) {
        List<QueryItemDTO> children = new ArrayList<>();
        if (node.getConditions() == null) {
            return children;
        }
        for (QueryItemDTO condition : node.getConditions()) {
            if (condition != null) {
                children.add(condition);
            }
        }
        return children;
    }
}
