package bio.cosy.feddb.core.api.query;

import io.quarkus.logging.Log;
import lombok.Getter;

@Getter
public enum QueryOperatorTypes {
    EQUAL("=", "="),
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

    /** Lang2Query spelling from the paper, such as {@code =} or {@code exists}. */
    public String lang2Operator() {
        return inputOperator;
    }

    public static QueryOperatorTypes fromOperator(String operator) {
        if (operator == null || operator.isBlank()) {
            return null;
        }

        String trimmed = operator.trim();
        // Legacy alias for the paper's "=". Compared exactly so it never collides with "=".
        if ("==".equals(trimmed)) {
            return EQUAL;
        }
        for (QueryOperatorTypes type : values()) {
            if (type.inputOperator.equalsIgnoreCase(trimmed) || type.name().equalsIgnoreCase(trimmed)) {
                return type;
            }
        }

        Log.errorf("Unsupported operator: %s", operator);
        return null;
    }
}
