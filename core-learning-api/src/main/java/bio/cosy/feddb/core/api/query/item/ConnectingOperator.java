package bio.cosy.feddb.core.api.query.item;

import lombok.Getter;

@Getter
public enum ConnectingOperator {
    AND("INTERSECT"),
    OR("UNION");

    private final String sqlSetOperator;

    ConnectingOperator(String sqlSetOperator) {
        this.sqlSetOperator = sqlSetOperator;
    }
}
