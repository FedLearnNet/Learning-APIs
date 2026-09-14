package bio.cosy.feddb.local.api.query;

/**
 * Thrown when a query cannot be evaluated and must be rejected instead of
 * silently returning an empty result. This happens, for example, when an
 * AND-combined query item references an ontology that does not resolve to any
 * schema node on this node: intersecting such an item with the others would
 * collapse the whole result to zero, which is indistinguishable from "no
 * patients match" and is therefore misleading.
 */
public class QueryRejectedException extends RuntimeException {

    public QueryRejectedException(String message) {
        super(message);
    }
}
