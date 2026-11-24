package bio.cosy.feddb.local.api.query.builder;

import bio.cosy.feddb.core.api.datamodler.datatype.DataTypes;
import bio.cosy.feddb.core.api.query.QueryOperatorTypes;
import bio.cosy.feddb.core.api.query.QueryRejectedException;
import bio.cosy.feddb.core.api.query.item.IntervalPartDTO;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Checks and SQL text of query values and data types, shared by the query builder.
 */
public final class QueryValueHelper {

    // also used as SQL regex, so Java and PostgreSQL agree on what a number is
    public static final String NUMBER = "^-?[0-9]+([.][0-9]+)?$";

    /**
     * True for data types stored in value_string.
     */
    public static boolean isText(DataTypes dataType) {
        return dataType == DataTypes.STRING || dataType == DataTypes.CATEGORICAL;
    }

    /**
     * False for text operators such as CONTAINS on columns that hold no text.
     */
    public static boolean isOperatorValidForDataType(QueryOperatorTypes operator, DataTypes dataType) {
        return switch (operator) {
            case CONTAINS, NOT_CONTAINS, START_WIDTH, END_WIDTH, REGEX -> isText(dataType);
            default -> true;
        };
    }

    /**
     * True for data types a number can be read from.
     */
    public static boolean isNumericReadable(DataTypes dataType) {
        return dataType == DataTypes.INT || dataType == DataTypes.FLOAT || isText(dataType);
    }

    /**
     * True for whole and decimal numbers.
     */
    public static boolean isNumber(String value) {
        return value != null && value.trim().matches(NUMBER);
    }

    /**
     * The value, rejected when blank.
     */
    public static String requireValue(String value) {
        if (StringUtils.isBlank(value)) {
            throw new QueryRejectedException("Operator is missing a value");
        }
        return value;
    }

    /**
     * Interval text such as "2 weeks 3 days"; QueryValidationBO checks the parts before.
     */
    public static String intervalSql(List<IntervalPartDTO> parts) {
        List<String> texts = new ArrayList<>();
        for (IntervalPartDTO part : parts) {
            texts.add(part.getCount() + " " + part.getUnit().getSqlUnit());
        }
        return String.join(" ", texts);
    }
}
