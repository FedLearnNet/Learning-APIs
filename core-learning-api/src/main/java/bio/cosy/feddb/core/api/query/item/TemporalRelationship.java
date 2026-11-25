package bio.cosy.feddb.core.api.query.item;

public enum TemporalRelationship {
    WITHIN,
    IN_THE_FOLLOWING,
    SAME_TIME,
    ANYTIME_AFTER,
    SAME_VISIT,
    SAME_GROUP;

    public boolean needsInterval() {
        return this == WITHIN || this == IN_THE_FOLLOWING;
    }
}
