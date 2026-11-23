package bio.cosy.feddb.local.api.query.builder;

import bio.cosy.feddb.core.api.datamodler.datatype.DataTypes;
import bio.cosy.feddb.core.api.query.QueryOperatorTypes;
import bio.cosy.feddb.core.api.query.item.QueryItemDTO;
import bio.cosy.feddb.core.api.query.item.TimeFilterDTO;
import bio.cosy.feddb.core.api.query.QueryRejectedException;
import jakarta.enterprise.context.ApplicationScoped;
import org.apache.commons.lang3.BooleanUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;

/** Writes operator and value of a condition as SQL; values become parsed literals, never raw SQL. */
@ApplicationScoped
public class QueryPredicateBO {

    /** Column of patient_data holding values of the data type. */
    public String columnSql(DataTypes dataType) {
        return switch (dataType) {
            case STRING, CATEGORICAL -> "value_string";
            case INT -> "value_int";
            case FLOAT -> "value_float";
            case BOOLEAN -> "value_boolean";
            case FILE -> "value_blob";
            case DATE -> "value_date";
            case DATE_TIME -> "value_date_time";
        };
    }

    /** Typed column, falling back to value_string when it holds a number. */
    public String numericValueSql(DataTypes dataType) {
        String parsed = "CASE WHEN btrim(value_string) ~ '" + QueryValueHelper.NUMBER + "' THEN btrim(value_string)::numeric END";
        return switch (dataType) {
            case INT -> "COALESCE(value_int, " + parsed + ")";
            case FLOAT -> "COALESCE(value_float, " + parsed + ")";
            case STRING, CATEGORICAL -> parsed;
            default -> throw new QueryRejectedException("Numeric comparison cannot read datatype " + dataType);
        };
    }

    /** Number comparison of the value with the leaf value. */
    public String comparisonSql(QueryItemDTO leaf, String valueSql) {
        if (!leaf.getOperator().isComparison()) {
            throw new QueryRejectedException(leaf.getOperator() + " cannot compare a number");
        }
        return valueSql + " " + leaf.getOperator().getSqlOperator() + " " + literal(DataTypes.FLOAT, leaf.getValue());
    }

    /** Operator of the leaf applied to the value, typed by the column. */
    public String operatorSql(QueryItemDTO leaf, DataTypes dataType, String valueSql) {
        QueryOperatorTypes operator = leaf.getOperator();
        String value = leaf.getValue();
        String sql = valueSql + " " + operator.getSqlOperator() + " ";
        return switch (operator) {
            case EXISTS, NOT_EXISTS -> valueSql + " " + operator.getSqlOperator();
            case CONTAINS, NOT_CONTAINS -> sql + likePattern("%", value, "%");
            case START_WIDTH -> sql + likePattern("", value, "%");
            case END_WIDTH -> sql + likePattern("%", value, "");
            case REGEX -> sql + literal(dataType, checkedRegex(value));
            case IN, NOT_IN -> sql + "(" + Arrays.stream(QueryValueHelper.requireValue(value).split(","))
                    .map(String::trim)
                    .map(item -> literal(dataType, item))
                    .collect(Collectors.joining(", ")) + ")";
            case HAS_CHANGED, CHANGED_FROM, CHANGED_TO -> throw new QueryRejectedException(operator + " requires valueMode DELTA");
            default -> sql + literal(dataType, value);
        };
    }

    /** Condition for a change between previous and current value. */
    public String changeSql(QueryItemDTO leaf, DataTypes dataType, String currentSql, String previousSql) {
        String changed = currentSql + " IS DISTINCT FROM " + previousSql;
        return switch (leaf.getOperator()) {
            case CHANGED_FROM -> previousSql + " = " + literal(dataType, leaf.getValue()) + " AND " + changed;
            case CHANGED_TO -> currentSql + " = " + literal(dataType, leaf.getValue()) + " AND " + changed;
            default -> changed;
        };
    }

    /** " AND ..." on visit_timestamp, empty for anytime. */
    public String timeFilterSql(TimeFilterDTO timeFilter) {
        if (timeFilter == null) {
            return "";
        }
        String sql = "";
        if (timeFilter.getAfter() != null) {
            sql += " AND visit_timestamp > date '" + timeFilter.getAfter() + "'";
        }
        if (timeFilter.getBefore() != null) {
            sql += " AND visit_timestamp < date '" + timeFilter.getBefore() + "'";
        }
        return sql;
    }

    // literal for the schema node's own type
    private String literal(DataTypes dataType, String value) {
        String text = QueryValueHelper.requireValue(value).trim();
        try {
            return switch (dataType) {
                case INT -> Long.toString(Long.parseLong(text));
                case FLOAT -> floatLiteral(text);
                // only true and false in any case, IllegalArgumentException otherwise
                case BOOLEAN -> BooleanUtils.toBooleanObject(text.toLowerCase(Locale.ROOT), "true", "false", null).toString();
                case DATE -> "'" + LocalDate.parse(text) + "'";
                case DATE_TIME -> "'" + dateTimeLiteral(text) + "'";
                case STRING, CATEGORICAL, FILE -> "'" + value.replace("'", "''") + "'";
            };
        } catch (IllegalArgumentException | DateTimeParseException e) {
            throw new QueryRejectedException("Value does not match datatype " + dataType + ": " + text);
        }
    }

    private String floatLiteral(String text) {
        if (!text.matches(QueryValueHelper.NUMBER)) {
            throw new NumberFormatException(text);
        }
        return new BigDecimal(text).toPlainString();
    }

    // date-only values count as midnight UTC
    private String dateTimeLiteral(String text) {
        if (text.contains("T")) {
            return OffsetDateTime.parse(text).toString();
        }
        return LocalDate.parse(text).toString();
    }

    private String likePattern(String prefix, String value, String suffix) {
        String escaped = QueryValueHelper.requireValue(value).replace("'", "''").replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return "'" + prefix + escaped + suffix + "' ESCAPE '\\'";
    }

    // an invalid pattern must fail here, not on matching rows
    private String checkedRegex(String pattern) {
        try {
            Pattern.compile(QueryValueHelper.requireValue(pattern));
            return pattern;
        } catch (PatternSyntaxException e) {
            throw new QueryRejectedException("Regular expression is not valid");
        }
    }
}
