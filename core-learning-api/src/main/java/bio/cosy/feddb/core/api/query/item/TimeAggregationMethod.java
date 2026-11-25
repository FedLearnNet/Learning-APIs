package bio.cosy.feddb.core.api.query.item;

// WITHIN averages inside timeWindow, WITHIN_AT_* counts matches inside timeWindow against timeWindowCount
public enum TimeAggregationMethod {
    NONE,
    FIRST,
    LAST,
    WITHIN,
    WITHIN_AT_LEAST,
    WITHIN_AT_MOST;

    public boolean needsCount() {
        return this == WITHIN_AT_LEAST || this == WITHIN_AT_MOST;
    }
}
