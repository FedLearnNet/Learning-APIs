package bio.cosy.feddb.local.api.query;

import bio.cosy.feddb.core.api.datamodler.datatype.DataTypes;
import bio.cosy.feddb.core.api.query.Lang2QueryDocuments;
import bio.cosy.feddb.core.api.query.Lang2QueryNodeDTO;
import bio.cosy.feddb.core.api.query.QueryDTO;
import bio.cosy.feddb.core.api.query.QueryItemDTO;
import bio.cosy.feddb.core.api.query.QueryOperatorDTO;
import bio.cosy.feddb.core.api.query.QueryOperatorTypes;
import bio.cosy.feddb.local.api.schema.datatype.DataTypeEntity;
import bio.cosy.feddb.local.api.schema.schemanode.SchemaNodeAO;
import bio.cosy.feddb.local.api.schema.schemanode.SchemaNodeEntity;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@ApplicationScoped
public class QueryBuilderBO {
    private static final String TABLE_NAME = "patient_data";
    private static final String PATIENT_ID_NAME = "patient_id";
    private static final String SCHEMA_ID_NAME = "schema_node_id";
    private static final String VISIT_TIMESTAMP_NAME = "visit_timestamp";
    private static final String RESULT_ALIAS = "query_result";
    private static final String EMPTY_PATIENT_QUERY =
            "SELECT DISTINCT " + PATIENT_ID_NAME + " FROM " + TABLE_NAME + " WHERE 1 = 0";
    private static final Pattern INTERVAL_PART = Pattern.compile("(\\d+)\\s+(minutes?|hours?|days?|weeks?|months?|years?)");
    private static final Pattern NUMERIC_LITERAL = Pattern.compile("-?\\d+(?:\\.\\d+)?");
    private static final String INTEGER_TEXT = "^-?[0-9]+$";
    private static final String FLOAT_TEXT = "^-?[0-9]+([.][0-9]+)?$";

    @Inject
    SchemaNodeAO schemaNodeAO;

    /**
     * Builds a SQL to count distinct patient IDs matching all query items via INTERSECT
     */
    public String buildCountDistinctSql(QueryDTO dto) {
        if (dto == null || dto.getQuery() == null || isEmptyGroup(dto.getQuery())) {
            return EMPTY_PATIENT_QUERY;
        }
        return wrapDistinct(buildNode(dto.getQuery()));
    }

    private boolean isEmptyGroup(Lang2QueryNodeDTO node) {
        return node.isGroup()
                && (node.getConditions() == null || node.getConditions().isEmpty())
                && (node.getOntologyId() == null || node.getOntologyId().isBlank());
    }

    private String wrapDistinct(String inner) {
        if (inner == null || inner.isBlank() || EMPTY_PATIENT_QUERY.equals(inner)) {
            return EMPTY_PATIENT_QUERY;
        }
        return "SELECT DISTINCT " + PATIENT_ID_NAME + " FROM (" + inner + ") AS " + RESULT_ALIAS;
    }

    private String joinClauses(List<String> clauses, String separator) {
        if (clauses == null || clauses.isEmpty()) {
            return EMPTY_PATIENT_QUERY;
        }
        if (clauses.size() == 1) {
            return clauses.getFirst();
        }
        return String.join(separator, clauses);
    }

    private String buildNode(Lang2QueryNodeDTO node) {
        if (node == null) {
            throw new QueryRejectedException("Lang2Query node is missing");
        }
        rejectUnsupported(node);
        if (node.isGroup()) {
            return buildGroup(node);
        }
        ValueMode valueMode = parseValueMode(node.getValueMode());
        if (isChangeOperator(node.getOperator())) {
            return buildChangeClause(node, valueMode);
        }
        QueryItemDTO item = Lang2QueryDocuments.toQueryItem(node);
        if (item == null || item.getOperator() == null || item.getOperator().isEmpty()) {
            throw new QueryRejectedException("Lang2Query leaf is missing a supported operator");
        }
        if (valueMode.isAbsolute()) {
            return buildClause(item, node.getTimeFilter());
        }
        return buildDeltaClause(item, node.getTimeFilter(), valueMode, node.getDatatype());
    }

    private String buildGroup(Lang2QueryNodeDTO group) {
        String connectingOperator = group.getConnectingOperator().trim().toLowerCase(Locale.ROOT);
        if ("$or".equals(connectingOperator)) {
            Lang2QueryNodeDTO collapsed = collapseEqualOr(group);
            if (collapsed != null) {
                return buildNode(collapsed);
            }
        }
        String separator = switch (connectingOperator) {
            case "$and" -> " INTERSECT ";
            case "$or" -> " UNION ";
            default -> throw new QueryRejectedException(
                    "Unsupported connecting_operator: " + group.getConnectingOperator());
        };
        if (group.getConditions() == null || group.getConditions().isEmpty()) {
            throw new QueryRejectedException("Lang2Query group has no conditions");
        }
        if (group.getOntologyId() != null && !group.getOntologyId().isBlank()) {
            throw new QueryRejectedException("A Lang2Query group cannot also be a leaf condition");
        }
        List<String> clauses = group.getConditions().stream()
                .map(this::buildNode)
                .filter(Objects::nonNull)
                .filter(clause -> !clause.isBlank())
                .toList();
        String joined = joinClauses(clauses, separator);
        if (EMPTY_PATIENT_QUERY.equals(joined) || clauses.size() == 1) {
            return joined;
        }
        return "(" + joined + ")";
    }

    private Lang2QueryNodeDTO collapseEqualOr(Lang2QueryNodeDTO group) {
        if (group.getConditions() == null || group.getConditions().size() < 2) {
            return null;
        }
        String ontologyId = null;
        String dataTypeId = null;
        Object timeFilter = null;
        boolean timeFilterSet = false;
        List<String> values = new ArrayList<>();
        for (Lang2QueryNodeDTO child : group.getConditions()) {
            if (child == null || child.isGroup()) {
                return null;
            }
            rejectUnsupported(child);
            if (!parseValueMode(child.getValueMode()).isAbsolute()) {
                return null;
            }
            QueryOperatorTypes operator = QueryOperatorTypes.fromOperator(child.getOperator());
            if (operator != QueryOperatorTypes.EQUAL || child.getOntologyId() == null || child.getOntologyId().isBlank()) {
                return null;
            }
            String value = child.valueAsString();
            if (value == null || value.isBlank()) {
                return null;
            }
            if (ontologyId == null) {
                ontologyId = child.getOntologyId();
                dataTypeId = child.getDataTypeId();
            } else if (!ontologyId.equals(child.getOntologyId()) || !Objects.equals(dataTypeId, child.getDataTypeId())) {
                return null;
            }
            if (!timeFilterSet) {
                timeFilter = child.getTimeFilter();
                timeFilterSet = true;
            } else if (!Objects.equals(timeFilter, child.getTimeFilter())) {
                return null;
            }
            values.add(value);
        }
        Lang2QueryNodeDTO leaf = new Lang2QueryNodeDTO();
        leaf.setOntologyId(ontologyId);
        leaf.setDataTypeId(dataTypeId);
        leaf.setOperator("IN");
        leaf.setValue(String.join(",", values));
        leaf.setTimeFilter(timeFilter);
        return leaf;
    }

    private void rejectUnsupported(Lang2QueryNodeDTO node) {
        if (node.getTemporalRelations() != null && !node.getTemporalRelations().isEmpty()) {
            throw new QueryRejectedException("temporal_relations is not supported yet");
        }
        if (node.isGroup() && notBlank(node.getValueMode())) {
            throw new QueryRejectedException("value_mode is not allowed on a group");
        }
        ValueMode valueMode = node.isGroup() ? ValueMode.absolute() : parseValueMode(node.getValueMode());
        if (node.getTimeFilter() != null) {
            validateTimeFilter(node.getTimeFilter());
        }
        if (notBlank(node.getTimeAggregationMethod()) && !"none".equalsIgnoreCase(node.getTimeAggregationMethod())) {
            throw new QueryRejectedException("time_aggregation_method is not supported yet");
        }
        if (notBlank(node.getValueAggregationMethod()) && !"none".equalsIgnoreCase(node.getValueAggregationMethod())) {
            throw new QueryRejectedException("value_aggregation_method is not supported yet");
        }
        if (node.getAtLeast() != null || node.getAtMost() != null) {
            throw new QueryRejectedException("at_least / at_most is not supported yet");
        }
        if (notBlank(node.getScale())) {
            throw new QueryRejectedException("scale is not supported yet");
        }
        if (isChangeOperator(node.getOperator()) && valueMode.isAbsolute()) {
            throw new QueryRejectedException(node.getOperator() + " requires value_mode delta");
        }
    }

    private ValueMode parseValueMode(String raw) {
        if (raw == null || raw.isBlank() || "absolute".equalsIgnoreCase(raw.trim())) {
            return ValueMode.absolute();
        }
        String text = raw.trim().toLowerCase(Locale.ROOT);
        if ("delta".equals(text)) {
            return ValueMode.delta();
        }
        if (text.startsWith("delta")) {
            String rest = text.substring("delta".length()).stripLeading();
            if (!rest.isEmpty()) {
                return ValueMode.window(requireInterval(rest));
            }
        }
        throw new QueryRejectedException("Unsupported value_mode: " + raw);
    }

    private String requireInterval(String text) {
        String remaining = text.trim().toLowerCase(Locale.ROOT);
        List<String> parts = new ArrayList<>();
        while (!remaining.isEmpty()) {
            Matcher matcher = INTERVAL_PART.matcher(remaining);
            if (!matcher.lookingAt()) {
                throw new QueryRejectedException("Unsupported value_mode interval: " + text);
            }
            int count;
            try {
                count = Integer.parseInt(matcher.group(1));
            } catch (NumberFormatException e) {
                throw new QueryRejectedException("Unsupported value_mode interval: " + text);
            }
            if (count <= 0) {
                throw new QueryRejectedException("value_mode interval must be positive: " + text);
            }
            parts.add(count + " " + matcher.group(2));
            remaining = remaining.substring(matcher.end()).stripLeading();
        }
        return String.join(" ", parts);
    }

    private boolean isChangeOperator(String operator) {
        if (operator == null || operator.isBlank()) {
            return false;
        }
        String normalized = operator.trim().toLowerCase(Locale.ROOT);
        return normalized.equals("has_changed")
                || normalized.equals("changed_from")
                || normalized.equals("changed_to");
    }

    private String buildDeltaClause(QueryItemDTO item, Object timeFilter, ValueMode valueMode, String datatype) {
        String timeSql = timeSql(timeFilter);
        String predicate = deltaOperatorPredicate(item.getOperator());
        NumericParse requested = numericParse(datatype);
        if (requested == null) {
            requested = NumericParse.FLOAT;
        }
        String comparison = predicate.replace("value_delta", "(later.numeric_value - earlier.numeric_value)");
        List<String> perColumnClauses = new ArrayList<>();
        for (List<SchemaNodeEntity> group : columnsFor(item.getOntologyId(), item.getDataTypeId()).values()) {
            DataTypes dataType = group.getFirst().getDataType().getType();
            if (dataType != DataTypes.INT && dataType != DataTypes.FLOAT
                    && dataType != DataTypes.STRING && dataType != DataTypes.CATEGORICAL) {
                throw new QueryRejectedException(
                        "value_mode delta numeric comparison cannot read datatype " + dataType);
            }
            List<Long> schemaNodeIds = group.stream().map(SchemaNodeEntity::getId).toList();
            String source = "(" + numericDeltaSource(schemaNodeIds, dataType, timeSql, requested) + ")";
            if (valueMode.isWindowed()) {
                perColumnClauses.add(windowPairSelect(source, "", comparison, valueMode.interval()));
            } else {
                perColumnClauses.add(lagNumericSelect(source, predicate + " AND " + gapPredicate(valueMode)));
            }
        }
        return unionClauses(item.getOntologyId(), perColumnClauses);
    }

    private String buildChangeClause(Lang2QueryNodeDTO leaf, ValueMode valueMode) {
        String operator = leaf.getOperator().trim().toLowerCase(Locale.ROOT);
        if (!"has_changed".equals(operator) && (leaf.valueAsString() == null || leaf.valueAsString().isBlank())) {
            throw new QueryRejectedException(operator + " requires a value");
        }
        String timeSql = timeSql(leaf.getTimeFilter());
        List<String> perColumnClauses = new ArrayList<>();
        for (List<SchemaNodeEntity> group : columnsFor(leaf.getOntologyId(), leaf.getDataTypeId()).values()) {
            DataTypes dataType = group.getFirst().getDataType().getType();
            String columnName = getColumnName(dataType);
            String predicate = changePredicate(operator, dataType, leaf.valueAsString());
            List<Long> schemaNodeIds = group.stream().map(SchemaNodeEntity::getId).toList();
            if (valueMode.isWindowed()) {
                String pairPredicate = predicate
                        .replace("current_value", "later." + columnName)
                        .replace("previous_value", "earlier." + columnName);
                perColumnClauses.add(windowPairSelect(
                        TABLE_NAME,
                        nativePairWhere(schemaNodeIds, columnName, leaf.getTimeFilter()),
                        pairPredicate,
                        valueMode.interval()));
            } else {
                perColumnClauses.add(deltaSelect(
                        schemaNodeIds,
                        columnName + " AS current_value, LAG(" + columnName + ") OVER w AS previous_value",
                        columnName,
                        timeSql,
                        "previous_value IS NOT NULL AND " + predicate + " AND " + gapPredicate(valueMode)));
            }
        }
        return unionClauses(leaf.getOntologyId(), perColumnClauses);
    }

    private String deltaSelect(
            List<Long> schemaNodeIds,
            String projection,
            String columnName,
            String timeSql,
            String outerPredicate) {
        return "(SELECT DISTINCT "
                + PATIENT_ID_NAME
                + " FROM (SELECT "
                + PATIENT_ID_NAME
                + ", "
                + projection
                + ", "
                + VISIT_TIMESTAMP_NAME
                + " - LAG("
                + VISIT_TIMESTAMP_NAME
                + ") OVER w AS time_gap FROM "
                + TABLE_NAME
                + " WHERE "
                + schemaIdPredicate(schemaNodeIds)
                + " AND "
                + columnName
                + " IS NOT NULL AND "
                + VISIT_TIMESTAMP_NAME
                + " IS NOT NULL"
                + timeSql
                + " WINDOW w AS (PARTITION BY "
                + PATIENT_ID_NAME
                + ", "
                + SCHEMA_ID_NAME
                + " ORDER BY "
                + VISIT_TIMESTAMP_NAME
                + ", id)) steps WHERE "
                + outerPredicate
                + ")";
    }

    private String windowPairSelect(String sourceSql, String extraWhere, String comparison, String interval) {
        return "(SELECT DISTINCT later."
                + PATIENT_ID_NAME
                + " FROM "
                + sourceSql
                + " later JOIN "
                + sourceSql
                + " earlier ON earlier."
                + PATIENT_ID_NAME
                + " = later."
                + PATIENT_ID_NAME
                + " AND earlier."
                + SCHEMA_ID_NAME
                + " = later."
                + SCHEMA_ID_NAME
                + " AND earlier."
                + VISIT_TIMESTAMP_NAME
                + " < later."
                + VISIT_TIMESTAMP_NAME
                + " AND later."
                + VISIT_TIMESTAMP_NAME
                + " <= earlier."
                + VISIT_TIMESTAMP_NAME
                + " + interval '"
                + interval
                + "' WHERE "
                + comparison
                + extraWhere
                + ")";
    }

    private String nativePairWhere(List<Long> schemaNodeIds, String columnName, Object timeFilter) {
        String schemaPredicate = schemaIdPredicate(schemaNodeIds);
        String rowFilter = schemaPredicate
                + " AND "
                + columnName
                + " IS NOT NULL AND "
                + VISIT_TIMESTAMP_NAME
                + " IS NOT NULL"
                + timeSql(timeFilter);
        return " AND "
                + rowFilter.replace(SCHEMA_ID_NAME, "later." + SCHEMA_ID_NAME)
                        .replace(columnName, "later." + columnName)
                        .replace(VISIT_TIMESTAMP_NAME, "later." + VISIT_TIMESTAMP_NAME)
                + " AND "
                + rowFilter.replace(SCHEMA_ID_NAME, "earlier." + SCHEMA_ID_NAME)
                        .replace(columnName, "earlier." + columnName)
                        .replace(VISIT_TIMESTAMP_NAME, "earlier." + VISIT_TIMESTAMP_NAME);
    }

    private String numericDeltaSource(
            List<Long> schemaNodeIds,
            DataTypes dataType,
            String timeSql,
            NumericParse requested) {
        String parsed = "CASE WHEN btrim(value_string) ~ '"
                + requested.pattern()
                + "' THEN btrim(value_string)::numeric END";
        String valueExpr = switch (dataType) {
            case INT -> "COALESCE(value_int, " + parsed + ")";
            case FLOAT -> "COALESCE(value_float, " + parsed + ")";
            case STRING, CATEGORICAL -> parsed;
            default -> throw new QueryRejectedException(
                    "value_mode delta numeric comparison cannot read datatype " + dataType);
        };
        return "SELECT "
                + PATIENT_ID_NAME
                + ", "
                + SCHEMA_ID_NAME
                + ", "
                + VISIT_TIMESTAMP_NAME
                + ", id, "
                + valueExpr
                + " AS numeric_value FROM "
                + TABLE_NAME
                + " WHERE "
                + schemaIdPredicate(schemaNodeIds)
                + " AND "
                + VISIT_TIMESTAMP_NAME
                + " IS NOT NULL AND "
                + valueExpr
                + " IS NOT NULL"
                + timeSql;
    }

    private String lagNumericSelect(String sourceSql, String outerPredicate) {
        return "(SELECT DISTINCT "
                + PATIENT_ID_NAME
                + " FROM (SELECT "
                + PATIENT_ID_NAME
                + ", numeric_value - LAG(numeric_value) OVER w AS value_delta, "
                + VISIT_TIMESTAMP_NAME
                + " - LAG("
                + VISIT_TIMESTAMP_NAME
                + ") OVER w AS time_gap FROM "
                + sourceSql
                + " parsed WINDOW w AS (PARTITION BY "
                + PATIENT_ID_NAME
                + ", "
                + SCHEMA_ID_NAME
                + " ORDER BY "
                + VISIT_TIMESTAMP_NAME
                + ", id)) steps WHERE "
                + outerPredicate
                + ")";
    }

    private NumericParse numericParse(String datatype) {
        if (datatype == null || datatype.isBlank()) {
            return null;
        }
        String normalized = datatype.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "integer", "int" -> NumericParse.INTEGER;
            case "float" -> NumericParse.FLOAT;
            default -> null;
        };
    }

    private enum NumericParse {
        INTEGER(INTEGER_TEXT),
        FLOAT(FLOAT_TEXT);

        private final String pattern;

        NumericParse(String pattern) {
            this.pattern = pattern;
        }

        private String pattern() {
            return pattern;
        }
    }

    private String deltaOperatorPredicate(List<QueryOperatorDTO> operators) {
        if (operators == null || operators.isEmpty()) {
            throw new QueryRejectedException("value_mode delta is missing an operator");
        }
        List<String> parts = new ArrayList<>();
        for (QueryOperatorDTO operator : operators) {
            if (operator == null || operator.getOperator() == null) {
                throw new QueryRejectedException("value_mode delta is missing an operator");
            }
            String sqlOperator = switch (operator.getOperator()) {
                case EQUAL -> "=";
                case NOT_EQUAL -> "<>";
                case SMALLER -> "<";
                case SMALLER_EQUAL -> "<=";
                case BIGGER -> ">";
                case BIGGER_EQUAL -> ">=";
                default -> throw new QueryRejectedException(
                        "Operator is not valid for value_mode delta: " + operator.getOperator());
            };
            parts.add("value_delta " + sqlOperator + " " + requireNumericLiteral(operator.getValue()));
        }
        return String.join(" AND ", parts);
    }

    private String changePredicate(String operator, DataTypes dataType, String value) {
        String changed = "current_value IS DISTINCT FROM previous_value";
        return switch (operator) {
            case "has_changed" -> changed;
            case "changed_from" -> "previous_value = " + encodeValue(dataType, value) + " AND " + changed;
            case "changed_to" -> "current_value = " + encodeValue(dataType, value) + " AND " + changed;
            default -> throw new QueryRejectedException("Operator is not supported yet: " + operator);
        };
    }

    private String requireNumericLiteral(String value) {
        if (value == null || !NUMERIC_LITERAL.matcher(value.trim()).matches()) {
            throw new QueryRejectedException("value_mode delta requires a numeric value");
        }
        return value.trim();
    }

    private String gapPredicate(ValueMode valueMode) {
        if (valueMode.isWindowed()) {
            return "time_gap IS NOT NULL AND time_gap <= interval '" + valueMode.interval() + "'";
        }
        return "time_gap IS NOT NULL";
    }

    private String timeSql(Object timeFilter) {
        String timePredicate = timeFilterPredicate(timeFilter);
        return timePredicate == null ? "" : " AND " + timePredicate;
    }

    private String unionClauses(String ontologyId, List<String> clauses) {
        if (clauses == null || clauses.isEmpty()) {
            throw new QueryRejectedException(
                    "No evaluable clause could be built for ontology " + ontologyId
                            + "; required AND condition cannot be evaluated on this node");
        }
        return "(" + String.join(" UNION ", clauses) + ")";
    }

    private Map<String, List<SchemaNodeEntity>> columnsFor(String ontologyId, String dataTypeId) {
        if (ontologyId == null || ontologyId.isBlank()) {
            throw new QueryRejectedException("Query item is missing an ontologyId and cannot be evaluated");
        }
        List<SchemaNodeEntity> schemaNodes = getSchemaNodesForQueryItem(ontologyId, dataTypeId);
        if (schemaNodes.isEmpty()) {
            Log.warnf("Found no schema nodes for ontology %s", ontologyId);
            throw new QueryRejectedException("Required condition cannot be found on this node");
        }
        Log.infof("Found %d schema nodes for ontology %s", schemaNodes.size(), ontologyId);
        Map<String, List<SchemaNodeEntity>> nodesByColumn = new LinkedHashMap<>();
        for (SchemaNodeEntity schemaNode : schemaNodes) {
            DataTypeEntity dataType = schemaNode.getDataType();
            String columnName = getColumnName(dataType.getType());
            if (columnName == null) {
                Log.errorf("Unsupported data type for schema: %s", schemaNode.getName());
                continue;
            }
            String groupKey = dataType.getType().name() + "|" + columnName;
            nodesByColumn.computeIfAbsent(groupKey, key -> new ArrayList<>()).add(schemaNode);
        }
        if (nodesByColumn.isEmpty()) {
            throw new QueryRejectedException(
                    "No evaluable clause could be built for ontology " + ontologyId
                            + "; required AND condition cannot be evaluated on this node");
        }
        return nodesByColumn;
    }

    private record ValueMode(Kind kind, String interval) {
        private enum Kind {
            ABSOLUTE, DELTA, DELTA_WINDOW
        }

        private static ValueMode absolute() {
            return new ValueMode(Kind.ABSOLUTE, null);
        }

        private static ValueMode delta() {
            return new ValueMode(Kind.DELTA, null);
        }

        private static ValueMode window(String interval) {
            return new ValueMode(Kind.DELTA_WINDOW, interval);
        }

        private boolean isAbsolute() {
            return kind == Kind.ABSOLUTE;
        }

        private boolean isWindowed() {
            return kind == Kind.DELTA_WINDOW;
        }
    }

    private boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private boolean isAnytime(Object timeFilter) {
        return timeFilter instanceof String text && "anytime".equalsIgnoreCase(text);
    }

    private void validateTimeFilter(Object timeFilter) {
        timeFilterPredicate(timeFilter);
    }

    private String timeFilterPredicate(Object timeFilter) {
        if (timeFilter == null || isAnytime(timeFilter)) {
            return null;
        }
        if (!(timeFilter instanceof Map<?, ?> map)) {
            throw new QueryRejectedException("Unsupported time_filter: " + timeFilter);
        }
        Object after = mapValue(map, "after");
        Object before = mapValue(map, "before");
        if (after == null && before == null) {
            throw new QueryRejectedException("time_filter must be anytime or an after/before object");
        }
        List<String> parts = new ArrayList<>();
        if (after != null) {
            parts.add(VISIT_TIMESTAMP_NAME + " > date '" + requireIsoDate(after) + "'");
        }
        if (before != null) {
            parts.add(VISIT_TIMESTAMP_NAME + " < date '" + requireIsoDate(before) + "'");
        }
        return String.join(" AND ", parts);
    }

    private Object mapValue(Map<?, ?> map, String key) {
        if (map.containsKey(key)) {
            return map.get(key);
        }
        return null;
    }

    private String requireIsoDate(Object value) {
        String text = String.valueOf(value).trim();
        try {
            LocalDate.parse(text);
        } catch (DateTimeParseException e) {
            throw new QueryRejectedException("time_filter date must be YYYY-MM-DD: " + text);
        }
        return text;
    }

    /**
     * Builds a clause for a single QueryItemDTO scoped to schema nodes and operators.
     * <p>
     * Each item is combined with the others via {@code INTERSECT} (logical AND), so an
     * item that cannot be resolved must NOT be turned into an empty ({@code WHERE 1 = 0})
     * clause: doing so would silently collapse the whole result to zero. Instead the
     * query is rejected via {@link QueryRejectedException}.
     */
    public String buildClause(QueryItemDTO item) {
        return buildClause(item, null);
    }

    public String buildClause(QueryItemDTO item, Object timeFilter) {
        if (item == null || item.getOntologyId() == null || item.getOntologyId().isBlank()) {
            throw new QueryRejectedException("Query item is missing an ontologyId and cannot be evaluated");
        }

        List<SchemaNodeEntity> schemaNodes = getSchemaNodesForQueryItem(item.getOntologyId(), item.getDataTypeId());
        if (schemaNodes.isEmpty()) {
            Log.warnf("Found no schema nodes for ontology %s", item.getOntologyId());
            throw new QueryRejectedException("Required condition cannot be found on this node");
        }else{
            Log.infof("Found %d schema nodes for ontology %s", schemaNodes.size(), item.getOntologyId());
        }

        Map<String, List<SchemaNodeEntity>> nodesByColumn = new LinkedHashMap<>();
        for (SchemaNodeEntity schemaNode : schemaNodes) {
            DataTypeEntity dataType = schemaNode.getDataType();
            String columnName = getColumnName(dataType.getType());
            if (columnName == null) {
                Log.errorf("Unsupported data type for schema: %s", schemaNode.getName());
                continue;
            }
            String groupKey = dataType.getType().name() + "|" + columnName;
            nodesByColumn.computeIfAbsent(groupKey, key -> new ArrayList<>()).add(schemaNode);
        }

        String timePredicate = timeFilterPredicate(timeFilter);
        String timeSql = timePredicate == null ? "" : " AND " + timePredicate;

        List<String> perColumnClauses = new ArrayList<>();
        for (List<SchemaNodeEntity> group : nodesByColumn.values()) {
            DataTypes dataType = group.getFirst().getDataType().getType();
            String columnName = getColumnName(dataType);
            String operatorClause = buildOperatorClause(item.getOperator(), dataType, columnName);
            if (operatorClause == null || operatorClause.isBlank()) {
                continue;
            }
            List<Long> schemaNodeIds = group.stream().map(SchemaNodeEntity::getId).toList();
            perColumnClauses.add("(SELECT DISTINCT "
                    + PATIENT_ID_NAME
                    + " FROM "
                    + TABLE_NAME
                    + " WHERE "
                    + schemaIdPredicate(schemaNodeIds)
                    + " AND "
                    + operatorClause
                    + timeSql
                    + ")");
        }

        if (perColumnClauses.isEmpty()) {
            throw new QueryRejectedException(
                    "No evaluable clause could be built for ontology " + item.getOntologyId()
                            + "; required AND condition cannot be evaluated on this node");
        }
        return "(" + String.join(" UNION ", perColumnClauses) + ")";
    }

    private String schemaIdPredicate(List<Long> schemaNodeIds) {
        if (schemaNodeIds.size() == 1) {
            return SCHEMA_ID_NAME + " = " + schemaNodeIds.getFirst();
        }
        return SCHEMA_ID_NAME + " IN (" + schemaNodeIds.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(", ")) + ")";
    }

    public List<SchemaNodeEntity> getSchemaNodesForQueryItem(String ontologyId, String dataTypeId) {
        if (dataTypeId == null || dataTypeId.isBlank()) {
            return schemaNodeAO.findByOntologyGlobalId(ontologyId);
        }
        return schemaNodeAO.findByOntologyAndDatatypeGlobalId(ontologyId, dataTypeId);
    }

    private String getColumnName(DataTypes dataType) {
        return switch (dataType) {
            case STRING, CATEGORICAL -> "value_string";
            case INT -> "value_int";
            case FLOAT -> "value_float";
            case BOOLEAN -> "value_boolean";
            case FILE -> "value_blob";
            case DATE -> "value_date";
            case DATE_TIME -> "value_date_time";
            default -> {
                Log.errorf("Unsupported data type: %s", dataType);
                yield null;
            }
        };
    }

    private String encodeValue(DataTypes dataType, String value) {
        String sanitizedValue = sanitizeSqlLiteral(value);
        return switch (dataType) {
            case STRING, CATEGORICAL, DATE, DATE_TIME, FILE -> "'" + sanitizedValue + "'";
            case BOOLEAN -> sanitizedValue == null ? "null" : sanitizedValue.toLowerCase(Locale.ROOT);
            default -> sanitizedValue;
        };
    }

    private String mapOperator(QueryOperatorTypes operator) {
        // TODO: Use an enum for operators
        return switch (operator) {
            case EQUAL -> QueryOperatorTypes.EQUAL.getSqlOperator();
            case SMALLER -> QueryOperatorTypes.SMALLER.getSqlOperator();
            case SMALLER_EQUAL -> QueryOperatorTypes.SMALLER_EQUAL.getSqlOperator();
            case BIGGER -> QueryOperatorTypes.BIGGER.getSqlOperator();
            case BIGGER_EQUAL -> QueryOperatorTypes.BIGGER_EQUAL.getSqlOperator();
            case NOT_EQUAL -> QueryOperatorTypes.NOT_EQUAL.getSqlOperator();
            case IN -> QueryOperatorTypes.IN.getSqlOperator();
            case NOT_IN -> QueryOperatorTypes.NOT_IN.getSqlOperator();
            case EXISTS -> QueryOperatorTypes.EXISTS.getSqlOperator();
            case REGEX -> QueryOperatorTypes.REGEX.getSqlOperator();
            default -> {
                Log.errorf("Unsupported operator: %s", operator);
                yield null;
            }
        };
    }

    private String buildOperatorClause(List<QueryOperatorDTO> operators, DataTypes dataType, String columnName) {
        if (operators == null || operators.isEmpty()) {
            return null;
        }

        List<String> subClauses = new ArrayList<>();
        for (QueryOperatorDTO op : operators) {
            String subClause = buildSingleOperatorClause(op, dataType, columnName);
            if (subClause == null || subClause.isBlank()) {
                return null;
            }
            subClauses.add(subClause);
        }
        return String.join(" AND ", subClauses);
    }

    private String buildSingleOperatorClause(QueryOperatorDTO op, DataTypes dataType, String columnName) {
        if (op == null || op.getOperator() == null) {
            return null;
        }

        return switch (op.getOperator()) {
            case EXISTS -> columnName + " " + QueryOperatorTypes.EXISTS.getSqlOperator();
            case NOT_EXISTS -> columnName + " " + QueryOperatorTypes.NOT_EXISTS.getSqlOperator();
            case CONTAINS -> buildLikeClause(dataType, columnName, op.getValue(), "%", "%");
            case START_WIDTH -> buildLikeClause(dataType, columnName, op.getValue(), "", "%");
            case END_WIDTH -> buildLikeClause(dataType, columnName, op.getValue(), "%", "");
            case IN, NOT_IN -> {
                String encodedList = encodeListValue(dataType, op.getValue());
                if (encodedList == null || encodedList.isBlank()) {
                    yield null;
                }
                yield columnName + " " + mapOperator(op.getOperator()) + " (" + encodedList + ")";
            }
            default -> {
                String mappedOperator = mapOperator(op.getOperator());
                if (mappedOperator == null) {
                    yield null;
                }
                String encodedValue = encodeValue(dataType, op.getValue());
                yield columnName + " " + mappedOperator + " " + encodedValue;
            }
        };
    }

    private String buildLikeClause(DataTypes dataType, String columnName, String value, String prefix, String suffix) {
        if (dataType != DataTypes.STRING && dataType != DataTypes.CATEGORICAL) {
            Log.errorf("Unsupported LIKE-style operator for data type: %s", dataType);
            return null;
        }
        String escapedValue = escapeLike(value);
        if (escapedValue == null) {
            return null;
        }
        return columnName + " ILIKE '" + prefix + escapedValue + suffix + "' ESCAPE '\\'";
    }

    private String encodeListValue(DataTypes dataType, String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        String trimmed = value.trim();
        if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
            trimmed = trimmed.substring(1, trimmed.length() - 1);
        }

        List<String> values = new ArrayList<>();
        for (String item : trimmed.split(",")) {
            String cleaned = item.trim();
            if ((cleaned.startsWith("\"") && cleaned.endsWith("\""))
                    || (cleaned.startsWith("'") && cleaned.endsWith("'"))) {
                cleaned = cleaned.substring(1, cleaned.length() - 1);
            }
            if (!cleaned.isBlank()) {
                values.add(encodeValue(dataType, cleaned));
            }
        }
        return values.isEmpty() ? null : String.join(", ", values);
    }

    private String sanitizeSqlLiteral(String value) {
        return value == null ? null : value.replace("'", "''");
    }

    private String escapeLike(String value) {
        String sanitizedValue = sanitizeSqlLiteral(value);
        if (sanitizedValue == null) {
            return null;
        }
        return sanitizedValue
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }

    private String normalizeOperator(String operator) {
        return operator == null ? "" : operator.replaceAll(" ", "").toLowerCase(Locale.ROOT);
    }

}
