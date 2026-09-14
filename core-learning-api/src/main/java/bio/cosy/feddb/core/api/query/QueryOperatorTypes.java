package bio.cosy.feddb.core.api.query;

import io.quarkus.logging.Log;
import lombok.Getter;

@Getter
public enum QueryOperatorTypes {
    EQUAL("==", "="),
    SMALLER("<", "<"),
    SMALLER_EQUAL("<=", "<="),
    BIGGER(">", ">"),
    BIGGER_EQUAL(">=", ">="),
    NOT_EQUAL("!=", "<>"),
    IN("in", "IN"),
    NOT_IN("!in", "NOT IN"),
    EXISTS("exists", "IS NOT NULL"),
    NOT_EXISTS("!exists", "IS NULL"),
    REGEX("regex", "~"),
    CONTAINS("contains", "LIKE"),
    NOT_CONTAINS("!contains", "NOT LIKE"),
    START_WIDTH("startsWith", "LIKE"),
    END_WIDTH("endsWith", "LIKE");

    private final String inputOperator;
    private final String sqlOperator;

    QueryOperatorTypes(String inputOperator, String sqlOperator) {
        this.inputOperator = inputOperator;
        this.sqlOperator = sqlOperator;
    }

    public static QueryOperatorTypes fromOperator(String operator) {
        if (operator == null) {
            return null;
        }

        String normalized = normalize(operator);
        for (QueryOperatorTypes type : values()) {
            if (type.inputOperator.equals(normalized)) {
                return type;
            }
        }

        Log.errorf("Unsupported operator: %s", operator);
        return null;
    }

    private static String normalize(String operator) {
        return operator == null ? null : operator.trim().toLowerCase();
    }
}
