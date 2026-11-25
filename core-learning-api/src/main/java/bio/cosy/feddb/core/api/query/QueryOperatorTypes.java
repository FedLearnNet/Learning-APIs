package bio.cosy.feddb.core.api.query;

import lombok.Getter;

@Getter
public enum QueryOperatorTypes {
    EQUAL("="),
    SMALLER("<"),
    SMALLER_EQUAL("<="),
    BIGGER(">"),
    BIGGER_EQUAL(">="),
    NOT_EQUAL("<>"),
    IN("IN"),
    NOT_IN("NOT IN"),
    EXISTS("IS NOT NULL"),
    NOT_EXISTS("IS NULL"),
    REGEX("~"),
    CONTAINS("ILIKE"),
    NOT_CONTAINS("NOT ILIKE"),
    START_WIDTH("ILIKE"),
    END_WIDTH("ILIKE"),
    // compare a value with the previous one, need value mode DELTA
    HAS_CHANGED(null),
    CHANGED_FROM(null),
    CHANGED_TO(null);

    private final String sqlOperator;

    QueryOperatorTypes(String sqlOperator) {
        this.sqlOperator = sqlOperator;
    }

    public boolean isComparison() {
        return switch (this) {
            case EQUAL, NOT_EQUAL, SMALLER, SMALLER_EQUAL, BIGGER, BIGGER_EQUAL -> true;
            default -> false;
        };
    }

    public boolean isChange() {
        return this == HAS_CHANGED || this == CHANGED_FROM || this == CHANGED_TO;
    }

    public boolean readsValue() {
        return this != EXISTS && this != NOT_EXISTS;
    }
}
