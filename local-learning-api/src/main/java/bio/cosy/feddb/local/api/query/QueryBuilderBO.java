package bio.cosy.feddb.local.api.query;

import bio.cosy.feddb.core.api.datamodler.datatype.DataTypes;
import bio.cosy.feddb.core.api.query.Lang2QueryDocuments;
import bio.cosy.feddb.core.api.query.Lang2QueryNodeDTO;
import bio.cosy.feddb.core.api.query.QueryDTO;
import bio.cosy.feddb.core.api.query.QueryItemDTO;
import bio.cosy.feddb.core.api.query.QueryOperatorDTO;
import bio.cosy.feddb.core.api.query.QueryOperatorTypes;
import bio.cosy.feddb.core.api.query.TemporalRelationDTO;
import bio.cosy.feddb.local.api.schema.datatype.DataTypeEntity;
import bio.cosy.feddb.local.api.schema.schemanode.SchemaNodeAO;
import bio.cosy.feddb.local.api.schema.schemanode.SchemaNodeEntity;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
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
        if (hasOccurrenceBound(node)) {
            return buildConditionCountClause(node);
        }
        ValueMode valueMode = parseValueMode(node.getValueMode());
        if (isChangeOperator(node.getOperator())) {
            return buildChangeClause(node, valueMode);
        }
        QueryItemDTO item = Lang2QueryDocuments.toQueryItem(node);
        if (item == null || item.getOperator() == null || item.getOperator().isEmpty()) {
            throw new QueryRejectedException("Lang2Query leaf is missing a supported operator");
        }
        TimeAggregation timeAggregation = parseTimeAggregationMethod(node.getTimeAggregationMethod());
        ValueAggregation valueAggregation = parseValueAggregationMethod(node.getValueAggregationMethod());
        if (!(timeAggregation instanceof TimeAggregation.None) || valueAggregation == ValueAggregation.AVERAGE) {
            return buildAggregatedClause(item, node, valueMode, timeAggregation, valueAggregation);
        }
        if (valueMode.isAbsolute()) {
            return buildClause(item, node.getTimeFilter(), node.getDatatype());
        }
        return buildDeltaClause(item, node.getTimeFilter(), valueMode, node.getDatatype());
    }

    private String buildGroup(Lang2QueryNodeDTO group) {
        if (group.getTemporalRelations() != null && !group.getTemporalRelations().isEmpty()) {
            return buildTemporalGroup(group);
        }
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

    /**
     * Temporal relations are additional constraints on the group's normal boolean result.
     * Each relation keeps occurrence identity until source and target have been joined;
     * only then is the result reduced to patient ids.
     */
    private String buildTemporalGroup(Lang2QueryNodeDTO group) {
        validateTemporalRelations(group);
        List<String> clauses = new ArrayList<>();

        boolean orGroup = "$or".equalsIgnoreCase(group.getConnectingOperator());
        boolean[] referenced = new boolean[group.getConditions().size()];
        for (TemporalRelationDTO relation : group.getTemporalRelations()) {
            referenced[relation.getSourceCondition()] = true;
            referenced[relation.getTargetCondition()] = true;
        }
        if (orGroup) {
            Lang2QueryNodeDTO booleanOnly = new Lang2QueryNodeDTO();
            booleanOnly.setConnectingOperator(group.getConnectingOperator());
            booleanOnly.setConditions(group.getConditions());
            clauses.add(buildGroup(booleanOnly));
        } else {
            // A relation clause already proves both endpoint conditions. Re-evaluating
            // those leaves independently would undo same-group scoping for first/last.
            for (int i = 0; i < group.getConditions().size(); i++) {
                Lang2QueryNodeDTO condition = group.getConditions().get(i);
                if (!referenced[i]) {
                    clauses.add(buildNode(condition));
                } else if (hasOccurrenceBound(condition)) {
                    // The relation already proves the endpoint. Count that leaf's evaluated
                    // matches without also adding an unscoped boolean match.
                    clauses.add(buildConditionCountClause(condition));
                }
            }
        }

        for (TemporalRelationDTO relation : group.getTemporalRelations()) {
            Lang2QueryNodeDTO source = group.getConditions().get(relation.getSourceCondition());
            Lang2QueryNodeDTO target = group.getConditions().get(relation.getTargetCondition());
            TemporalRelation parsed = parseTemporalRelation(relation);
            String sourceOccurrences = buildTemporalOccurrences(source, null);
            String targetScope = parsed.kind() == TemporalRelationKind.SAME_GROUP
                    ? sameGroupScope(sourceOccurrences)
                    : null;
            String targetOccurrences = buildTemporalOccurrences(target, targetScope);
            clauses.add(buildTemporalRelationClause(sourceOccurrences, targetOccurrences, parsed));
        }
        return "(" + String.join(" INTERSECT ", clauses) + ")";
    }

    private void validateTemporalRelations(Lang2QueryNodeDTO group) {
        List<Lang2QueryNodeDTO> conditions = group.getConditions();
        if (conditions == null || conditions.isEmpty()) {
            throw new QueryRejectedException("A group with temporal_relations must have conditions");
        }
        for (TemporalRelationDTO relation : group.getTemporalRelations()) {
            if (relation == null || relation.getSourceCondition() == null || relation.getTargetCondition() == null) {
                throw new QueryRejectedException(
                        "temporal_relations requires source_condition and target_condition");
            }
            int source = relation.getSourceCondition();
            int target = relation.getTargetCondition();
            if (source < 0 || source >= conditions.size() || target < 0 || target >= conditions.size()) {
                throw new QueryRejectedException("temporal_relations condition index is out of range");
            }
            if (source == target) {
                throw new QueryRejectedException(
                        "temporal_relations source_condition and target_condition must differ");
            }
            if (conditions.get(source) == null || conditions.get(target) == null
                    || conditions.get(source).isGroup() || conditions.get(target).isGroup()) {
                throw new QueryRejectedException(
                        "temporal_relations endpoints must be leaf conditions in the same group");
            }
            if (isWholeSeriesAverage(conditions.get(source))
                    || isWholeSeriesAverage(conditions.get(target))) {
                throw new QueryRejectedException(
                        "A whole-series average has no occurrence time and cannot be a temporal relation endpoint");
            }
            if (relation.getAtLeast() != null && relation.getAtLeast() < 0
                    || relation.getAtMost() != null && relation.getAtMost() < 0) {
                throw new QueryRejectedException("temporal_relations counts cannot be negative");
            }
            if (relation.getAtLeast() != null && relation.getAtMost() != null
                    && relation.getAtLeast() > relation.getAtMost()) {
                throw new QueryRejectedException("temporal_relations at_least cannot exceed at_most");
            }
            parseTemporalRelation(relation);
        }
    }

    private boolean isWholeSeriesAverage(Lang2QueryNodeDTO leaf) {
        return parseValueAggregationMethod(leaf.getValueAggregationMethod()) == ValueAggregation.AVERAGE
                && parseTimeAggregationMethod(leaf.getTimeAggregationMethod()) instanceof TimeAggregation.None;
    }

    private TemporalRelation parseTemporalRelation(TemporalRelationDTO relation) {
        if (relation.getRelationship() == null || relation.getRelationship().isBlank()) {
            throw new QueryRejectedException("temporal_relations relationship is missing");
        }
        String text = relation.getRelationship().trim().toLowerCase(Locale.ROOT);
        TemporalRelationKind kind;
        String interval = null;
        if (text.startsWith("within ")) {
            kind = TemporalRelationKind.WITHIN;
            interval = requireInterval(text.substring("within ".length()), "temporal relationship interval");
        } else if (text.startsWith("in the following ")) {
            kind = TemporalRelationKind.FOLLOWING;
            interval = requireInterval(
                    text.substring("in the following ".length()), "temporal relationship interval");
        } else {
            kind = switch (text) {
                case "same time" -> TemporalRelationKind.SAME_TIME;
                case "anytime after" -> TemporalRelationKind.ANYTIME_AFTER;
                case "same visit" -> TemporalRelationKind.SAME_VISIT;
                case "same group" -> TemporalRelationKind.SAME_GROUP; // @*TODO: We need to detect whether the query condition leaves have the same `import_schema_group_id` and group them accordingly
                default -> throw new QueryRejectedException(
                        "Unsupported temporal relationship: " + relation.getRelationship());
            };
        }
        int atLeast = relation.getAtLeast() == null
                ? (relation.getAtMost() == null ? 1 : 0)
                : relation.getAtLeast();
        return new TemporalRelation(kind, interval, atLeast, relation.getAtMost());
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
            if (child == null || child.isGroup() || hasOccurrenceBound(child)) {
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
            if (!node.isGroup()) {
                throw new QueryRejectedException("temporal_relations is only allowed on a group");
            }
            validateTemporalRelations(node);
        }
        if (node.isGroup() && notBlank(node.getValueMode())) {
            throw new QueryRejectedException("value_mode is not allowed on a group");
        }
        ValueMode valueMode = node.isGroup() ? ValueMode.absolute() : parseValueMode(node.getValueMode());
        if (node.getTimeFilter() != null) {
            validateTimeFilter(node.getTimeFilter());
        }
        TimeAggregation timeAggregation = parseTimeAggregationMethod(node.getTimeAggregationMethod());
        ValueAggregation valueAggregation = parseValueAggregationMethod(node.getValueAggregationMethod());
        boolean hasAggregation = !(timeAggregation instanceof TimeAggregation.None)
                || valueAggregation == ValueAggregation.AVERAGE;
        if (node.isGroup() && hasAggregation) {
            throw new QueryRejectedException(
                    "time_aggregation_method / value_aggregation_method is not allowed on a group");
        }
        if (valueAggregation == ValueAggregation.AVERAGE && !valueMode.isAbsolute()) {
            throw new QueryRejectedException("value_aggregation_method must be None if value_mode is not absolute");
        }
        if (timeAggregation instanceof TimeAggregation.WithinAverage && valueAggregation != ValueAggregation.AVERAGE) {
            throw new QueryRejectedException("\"within X\" is only allowed if value_aggregation_method = average");
        }
        if (timeAggregation instanceof TimeAggregation.WithinCount && valueAggregation == ValueAggregation.AVERAGE) {
            throw new QueryRejectedException(
                    "\"within X at least/at most\" is not allowed if value_aggregation_method = average");
        }
        if (hasAggregation && isChangeOperator(node.getOperator())) {
            throw new QueryRejectedException(
                    node.getOperator() + " does not support time_aggregation_method / value_aggregation_method yet");
        }
        if (node.getAtLeast() != null || node.getAtMost() != null) {
            if (node.isGroup()) {
                throw new QueryRejectedException("at_least / at_most is only allowed on a leaf condition");
            }
            if ((node.getAtLeast() != null && node.getAtLeast() < 0)
                    || (node.getAtMost() != null && node.getAtMost() < 0)) {
                throw new QueryRejectedException("at_least / at_most cannot be negative");
            }
            if (node.getAtLeast() != null && node.getAtMost() != null
                    && node.getAtLeast() > node.getAtMost()) {
                throw new QueryRejectedException("at_least cannot exceed at_most");
            }
        }
        if (notBlank(node.getDatatype()) && !isAllowedDatatype(node.getDatatype())) {
            throw new QueryRejectedException("Unsupported datatype: " + node.getDatatype());
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
        return requireInterval(text, "value_mode interval");
    }

    private String requireInterval(String text, String fieldLabel) {
        String remaining = text.trim().toLowerCase(Locale.ROOT);
        List<String> parts = new ArrayList<>();
        while (!remaining.isEmpty()) {
            Matcher matcher = INTERVAL_PART.matcher(remaining);
            if (!matcher.lookingAt()) {
                throw new QueryRejectedException("Unsupported " + fieldLabel + ": " + text);
            }
            int count;
            try {
                count = Integer.parseInt(matcher.group(1));
            } catch (NumberFormatException e) {
                throw new QueryRejectedException("Unsupported " + fieldLabel + ": " + text);
            }
            if (count <= 0) {
                throw new QueryRejectedException(fieldLabel + " must be positive: " + text);
            }
            parts.add(count + " " + matcher.group(2));
            remaining = remaining.substring(matcher.end()).stripLeading();
        }
        return String.join(" ", parts);
    }

    private static final Pattern WITHIN_COUNT_SUFFIX =
            Pattern.compile("(.*?)\\s+(at least|at most)\\s+(\\d+)\\s+times?\\s*$", Pattern.CASE_INSENSITIVE);

    private TimeAggregation parseTimeAggregationMethod(String raw) {
        if (raw == null || raw.isBlank() || "none".equalsIgnoreCase(raw.trim())) {
            return new TimeAggregation.None();
        }
        String text = raw.trim();
        if ("first".equalsIgnoreCase(text)) {
            return new TimeAggregation.First();
        }
        if ("last".equalsIgnoreCase(text)) {
            return new TimeAggregation.Last();
        }
        if (!text.toLowerCase(Locale.ROOT).startsWith("within ")) {
            throw new QueryRejectedException("Unsupported time_aggregation_method: " + raw);
        }
        String remainder = text.substring("within ".length()).trim();
        Matcher countMatcher = WITHIN_COUNT_SUFFIX.matcher(remainder);
        if (countMatcher.matches()) {
            String interval = requireInterval(countMatcher.group(1), "time_aggregation_method interval");
            boolean atLeast = "at least".equalsIgnoreCase(countMatcher.group(2));
            int times;
            try {
                times = Integer.parseInt(countMatcher.group(3));
            } catch (NumberFormatException e) {
                throw new QueryRejectedException("Unsupported time_aggregation_method: " + raw);
            }
            if (times <= 0) {
                throw new QueryRejectedException("time_aggregation_method count must be positive: " + raw);
            }
            return new TimeAggregation.WithinCount(interval, atLeast, times);
        }
        return new TimeAggregation.WithinAverage(requireInterval(remainder, "time_aggregation_method interval"));
    }

    private ValueAggregation parseValueAggregationMethod(String raw) {
        if (raw == null || raw.isBlank() || "none".equalsIgnoreCase(raw.trim())) {
            return ValueAggregation.NONE;
        }
        if ("average".equalsIgnoreCase(raw.trim())) {
            return ValueAggregation.AVERAGE;
        }
        throw new QueryRejectedException("Unsupported value_aggregation_method: " + raw);
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

    /**
     * The numeric value for a schema node whose data type may be numeric already, or a
     * string that happens to hold a number. For {@code INT}/{@code FLOAT} this reads the
     * typed column and falls back to parsing {@code value_string} when the typed column is
     * null. For {@code STRING}/{@code CATEGORICAL} this only reads the parsed string. Rows
     * where neither the typed column nor the string parses never compare as a number.
     */
    private String numericValueExpression(DataTypes dataType, NumericParse requested) {
        String parsed = "CASE WHEN btrim(value_string) ~ '"
                + requested.pattern()
                + "' THEN btrim(value_string)::numeric END";
        return switch (dataType) {
            case INT -> "COALESCE(value_int, " + parsed + ")";
            case FLOAT -> "COALESCE(value_float, " + parsed + ")";
            case STRING, CATEGORICAL -> parsed;
            default -> throw new QueryRejectedException(
                    "value_mode delta numeric comparison cannot read datatype " + dataType);
        };
    }

    private boolean isNumericValueDataType(DataTypes dataType) {
        return dataType == DataTypes.INT || dataType == DataTypes.FLOAT
                || dataType == DataTypes.STRING || dataType == DataTypes.CATEGORICAL;
    }

    /** True only if every operator is a magnitude comparison, never a text/existence operator. */
    private boolean isNumericComparisonOperators(List<QueryOperatorDTO> operators) {
        if (operators == null || operators.isEmpty()) {
            return false;
        }
        for (QueryOperatorDTO op : operators) {
            if (op == null || op.getOperator() == null) {
                return false;
            }
            boolean numeric = switch (op.getOperator()) {
                case EQUAL, NOT_EQUAL, SMALLER, SMALLER_EQUAL, BIGGER, BIGGER_EQUAL -> true;
                default -> false;
            };
            if (!numeric) {
                return false;
            }
        }
        return true;
    }

    /**
     * A numeric comparison clause for a leaf whose {@code datatype} hint is {@code Integer}
     * or {@code Float}: compares {@link #numericValueExpression} against the operator's
     * value(s) instead of the raw column, so a number stored as text is parsed and compared
     * numerically rather than lexicographically. Rows that do not parse are excluded, never
     * compared as strings.
     */
    private String numericOperatorClause(List<QueryOperatorDTO> operators, DataTypes dataType, NumericParse requested) {
        String valueExpr = numericValueExpression(dataType, requested);
        String predicate = deltaOperatorPredicate(operators).replace("value_delta", valueExpr);
        return valueExpr + " IS NOT NULL AND " + predicate;
    }

    private String numericDeltaSource(
            List<Long> schemaNodeIds,
            DataTypes dataType,
            String timeSql,
            NumericParse requested) {
        String valueExpr = numericValueExpression(dataType, requested);
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

    /**
     * Counts how many evaluated matches a leaf has after time_filter, value_mode, and
     * aggregation. Omitted bounds stay on the normal patient-id SQL, which is at_least 1.
     * at_most with no positive minimum includes patients who never matched.
     */
    private boolean hasOccurrenceBound(Lang2QueryNodeDTO node) {
        return node.getAtLeast() != null || node.getAtMost() != null;
    }

    private String buildConditionCountClause(Lang2QueryNodeDTO leaf) {
        int atLeast = leaf.getAtLeast() == null ? 0 : leaf.getAtLeast();
        Integer atMost = leaf.getAtMost();
        if (atMost == null && atLeast <= 0) {
            return "(SELECT id AS patient_id FROM patient_meta)";
        }
        String occurrences = buildTemporalOccurrences(leaf, null);
        if (atLeast > 0) {
            List<String> having = new ArrayList<>();
            having.add("COUNT(DISTINCT occurrence_key) >= " + atLeast);
            if (atMost != null) {
                having.add("COUNT(DISTINCT occurrence_key) <= " + atMost);
            }
            return "(SELECT patient_id FROM "
                    + occurrences
                    + " counted GROUP BY patient_id HAVING "
                    + String.join(" AND ", having)
                    + ")";
        }
        return "(SELECT p.patient_id FROM (SELECT id AS patient_id FROM patient_meta) p LEFT JOIN "
                + occurrences
                + " counted ON counted.patient_id = p.patient_id"
                + " GROUP BY p.patient_id HAVING COUNT(DISTINCT counted.occurrence_key) <= "
                + atMost
                + ")";
    }

    private boolean isAllowedDatatype(String datatype) {
        return switch (datatype.trim().toLowerCase(Locale.ROOT)) {
            case "string", "integer", "int", "float", "boolean" -> true;
            default -> false;
        };
    }

    // ---- temporal_relations ---------------------------------------------------------------

    /**
     * Builds matching occurrences rather than patient ids. Keeping row identity here is
     * what allows relation joins to correlate sibling conditions without accidentally
     * matching unrelated observations belonging to the same patient.
     */
    private String buildTemporalOccurrences(Lang2QueryNodeDTO leaf, String rawScopePredicate) {
        rejectUnsupported(leaf);
        QueryItemDTO item = Lang2QueryDocuments.toQueryItem(leaf);
        if (item == null || item.getOperator() == null || item.getOperator().isEmpty()) {
            throw new QueryRejectedException("Temporal relation leaf is missing a supported operator");
        }
        ValueMode valueMode = parseValueMode(leaf.getValueMode());
        TimeAggregation timeAggregation = parseTimeAggregationMethod(leaf.getTimeAggregationMethod());
        ValueAggregation valueAggregation = parseValueAggregationMethod(leaf.getValueAggregationMethod());
        List<String> clauses = new ArrayList<>();
        for (List<SchemaNodeEntity> group : columnsFor(item.getOntologyId(), item.getDataTypeId()).values()) {
            DataTypes dataType = group.getFirst().getDataType().getType();
            List<Long> schemaNodeIds = group.stream().map(SchemaNodeEntity::getId).toList();
            String occurrence = buildTemporalColumnOccurrences(
                    item, leaf, valueMode, timeAggregation, valueAggregation,
                    dataType, schemaNodeIds, rawScopePredicate);
            // agg_value may have a different SQL type across local schema nodes. It is
            // no longer needed after a condition has matched, so normalize it before UNION.
            clauses.add("(SELECT patient_id, schema_node_id, event_time, id, occurrence_key, "
                    + "visit_id, import_schema_group_id, NULL::text AS agg_value FROM "
                    + occurrence
                    + " projected)");
        }
        return "(" + String.join(" UNION ALL ", clauses) + ")";
    }

    private String buildTemporalColumnOccurrences(
            QueryItemDTO item,
            Lang2QueryNodeDTO leaf,
            ValueMode valueMode,
            TimeAggregation timeAggregation,
            ValueAggregation valueAggregation,
            DataTypes dataType,
            List<Long> schemaNodeIds,
            String rawScopePredicate) {
        if (isChangeOperator(leaf.getOperator())) {
            return temporalChangeOccurrences(leaf, valueMode, dataType, schemaNodeIds, rawScopePredicate);
        }

        String source;
        String predicate;
        if (valueMode.isAbsolute()) {
            NumericParse requested = numericParse(leaf.getDatatype());
            boolean numeric = valueAggregation == ValueAggregation.AVERAGE
                    || requested != null
                    && isNumericComparisonOperators(item.getOperator())
                    && isNumericValueDataType(dataType);
            if (valueAggregation == ValueAggregation.AVERAGE && requested == null) {
                requested = NumericParse.FLOAT;
            }
            if (numeric && !isNumericValueDataType(dataType)) {
                throw new QueryRejectedException(
                        "value_aggregation_method average cannot read datatype " + dataType);
            }
            String valueExpr = numeric
                    ? numericValueExpression(dataType, requested)
                    : getColumnName(dataType);
            boolean requireValue = item.getOperator().stream()
                    .noneMatch(operator -> operator.getOperator() == QueryOperatorTypes.NOT_EXISTS);
            source = temporalRawSource(schemaNodeIds, valueExpr, leaf.getTimeFilter(), requireValue);
            source = applyTemporalScope(source, rawScopePredicate);
            predicate = numeric
                    ? deltaOperatorPredicate(item.getOperator()).replace("value_delta", "agg_value")
                    : buildOperatorClause(item.getOperator(), dataType, "agg_value");
            if (predicate == null || predicate.isBlank()) {
                throw new QueryRejectedException(
                        "Operator cannot be evaluated as a temporal relation occurrence");
            }
        } else {
            if (!isNumericValueDataType(dataType)) {
                throw new QueryRejectedException(
                        "value_mode delta numeric comparison cannot read datatype " + dataType);
            }
            NumericParse requested = numericParse(leaf.getDatatype());
            if (requested == null) {
                requested = NumericParse.FLOAT;
            }
            String numeric = temporalRawSource(
                    schemaNodeIds, numericValueExpression(dataType, requested), leaf.getTimeFilter(), true);
            numeric = applyTemporalScope(numeric, rawScopePredicate);
            source = valueMode.isWindowed()
                    ? temporalDeltaWindowSource(numeric, valueMode.interval())
                    : temporalDeltaSource(numeric);
            predicate = deltaOperatorPredicate(item.getOperator()).replace("value_delta", "agg_value");
        }

        if (valueAggregation == ValueAggregation.AVERAGE) {
            return temporalAverageOccurrences(source, predicate, timeAggregation);
        }
        if (timeAggregation instanceof TimeAggregation.First) {
            return temporalRankedOccurrences(source, predicate, true);
        }
        if (timeAggregation instanceof TimeAggregation.Last) {
            return temporalRankedOccurrences(source, predicate, false);
        }
        if (timeAggregation instanceof TimeAggregation.WithinCount withinCount) {
            return temporalWithinCountOccurrences(source, predicate, withinCount);
        }
        if (timeAggregation instanceof TimeAggregation.None) {
            return "(SELECT matched.* FROM " + source + " matched WHERE " + predicate + ")";
        }
        throw new QueryRejectedException("Unsupported temporal relation leaf aggregation");
    }

    private String temporalRawSource(
            List<Long> schemaNodeIds, String valueExpr, Object timeFilter, boolean requireValue) {
        return "(SELECT patient_id, schema_node_id, visit_timestamp AS event_time, id, "
                + "id::text AS occurrence_key, "
                + "visit_id, import_schema_group_id, "
                + valueExpr
                + " AS agg_value FROM patient_data WHERE "
                + schemaIdPredicate(schemaNodeIds)
                + " AND visit_timestamp IS NOT NULL"
                + (requireValue ? " AND " + valueExpr + " IS NOT NULL" : "")
                + timeSql(timeFilter)
                + ")";
    }

    private String applyTemporalScope(String source, String rawScopePredicate) {
        if (rawScopePredicate == null || rawScopePredicate.isBlank()) {
            return source;
        }
        return "(SELECT scoped.* FROM " + source + " scoped WHERE " + rawScopePredicate + ")";
    }

    private String sameGroupScope(String sourceOccurrences) {
        return "scoped.import_schema_group_id IS NOT NULL AND EXISTS (SELECT 1 FROM "
                + sourceOccurrences
                + " same_group_source WHERE same_group_source.patient_id = scoped.patient_id"
                + " AND same_group_source.import_schema_group_id = scoped.import_schema_group_id)";
    }

    private String temporalDeltaSource(String numericSource) {
        return "(SELECT patient_id, schema_node_id, event_time, id, occurrence_key, visit_id, "
                + "import_schema_group_id, value_delta AS agg_value FROM (SELECT parsed.*, "
                + "agg_value - LAG(agg_value) OVER w AS value_delta FROM "
                + numericSource
                + " parsed WINDOW w AS (PARTITION BY patient_id, schema_node_id "
                + "ORDER BY event_time, id)) delta_rows WHERE value_delta IS NOT NULL)";
    }

    private String temporalDeltaWindowSource(String numericSource, String interval) {
        return "(SELECT later.patient_id, later.schema_node_id, later.event_time, later.id, "
                + "(later.id::text || ':' || earlier.id::text) AS occurrence_key, "
                + "later.visit_id, later.import_schema_group_id, "
                + "(later.agg_value - earlier.agg_value) AS agg_value FROM "
                + numericSource
                + " later JOIN "
                + numericSource
                + " earlier ON earlier.patient_id = later.patient_id"
                + " AND earlier.schema_node_id = later.schema_node_id"
                + " AND earlier.event_time < later.event_time"
                + " AND later.event_time <= earlier.event_time + interval '"
                + interval
                + "')";
    }

    private String temporalRankedOccurrences(String source, String predicate, boolean first) {
        String order = first ? "ASC" : "DESC";
        return "(SELECT ranked.patient_id, ranked.schema_node_id, ranked.event_time, ranked.id, "
                + "ranked.occurrence_key, "
                + "ranked.visit_id, ranked.import_schema_group_id, ranked.agg_value FROM "
                + "(SELECT ranked_source.*, ROW_NUMBER() OVER (PARTITION BY patient_id, schema_node_id "
                + "ORDER BY event_time "
                + order
                + ", id "
                + order
                + ") rn FROM "
                + source
                + " ranked_source) ranked WHERE ranked.rn = 1 AND "
                + predicate
                + ")";
    }

    private String temporalWithinCountOccurrences(
            String source, String predicate, TimeAggregation.WithinCount withinCount) {
        String hits = "(SELECT * FROM " + source + " hit_source WHERE " + predicate + ")";
        String counts = "(SELECT w.patient_id, w.schema_node_id, w.event_time, w.id, "
                + "w.occurrence_key, w.visit_id, "
                + "w.import_schema_group_id, w.agg_value, COUNT(*) AS window_count FROM "
                + hits
                + " w JOIN "
                + hits
                + " r ON r.patient_id = w.patient_id AND r.schema_node_id = w.schema_node_id"
                + " AND r.event_time >= w.event_time"
                + " AND r.event_time < w.event_time + interval '"
                + withinCount.interval()
                + "' GROUP BY w.patient_id, w.schema_node_id, w.event_time, w.id, "
                + "w.occurrence_key, w.visit_id, "
                + "w.import_schema_group_id, w.agg_value)";
        if (withinCount.atLeast()) {
            return "(SELECT patient_id, schema_node_id, event_time, id, occurrence_key, visit_id, "
                    + "import_schema_group_id, agg_value FROM "
                    + counts
                    + " counts WHERE window_count >= "
                    + withinCount.times()
                    + ")";
        }
        return "(SELECT counts.patient_id, counts.schema_node_id, counts.event_time, counts.id, "
                + "counts.occurrence_key, "
                + "counts.visit_id, counts.import_schema_group_id, counts.agg_value FROM "
                + counts
                + " counts JOIN (SELECT patient_id, schema_node_id FROM "
                + counts
                + " grouped GROUP BY patient_id, schema_node_id HAVING MAX(window_count) <= "
                + withinCount.times()
                + ") eligible ON eligible.patient_id = counts.patient_id"
                + " AND eligible.schema_node_id = counts.schema_node_id)";
    }

    private String temporalAverageOccurrences(
            String source, String predicate, TimeAggregation timeAggregation) {
        if (timeAggregation instanceof TimeAggregation.WithinAverage within) {
            String averagePredicate = predicate.replace("agg_value", "AVG(r.agg_value)");
            return "(SELECT w.patient_id, w.schema_node_id, w.event_time, w.id, "
                    + "w.occurrence_key, w.visit_id, "
                    + "w.import_schema_group_id, AVG(r.agg_value) AS agg_value FROM "
                    + source
                    + " w JOIN "
                    + source
                    + " r ON r.patient_id = w.patient_id AND r.schema_node_id = w.schema_node_id"
                    + " AND r.event_time >= w.event_time"
                    + " AND r.event_time < w.event_time + interval '"
                    + within.interval()
                    + "' GROUP BY w.patient_id, w.schema_node_id, w.event_time, w.id, "
                    + "w.occurrence_key, w.visit_id, "
                    + "w.import_schema_group_id HAVING "
                    + averagePredicate
                    + ")";
        }
        if (timeAggregation instanceof TimeAggregation.First) {
            return temporalRankedOccurrences(source, predicate, true);
        }
        if (timeAggregation instanceof TimeAggregation.Last) {
            return temporalRankedOccurrences(source, predicate, false);
        }
        return "(SELECT s.patient_id, s.schema_node_id, MAX(s.event_time) AS event_time, "
                + "MAX(s.id) AS id, ('average:' || s.patient_id::text || ':' || s.schema_node_id::text) "
                + "AS occurrence_key, NULL::text AS visit_id, NULL::text AS import_schema_group_id, "
                + "AVG(s.agg_value) AS agg_value FROM "
                + source
                + " s GROUP BY s.patient_id, s.schema_node_id HAVING "
                + predicate.replace("agg_value", "AVG(s.agg_value)")
                + ")";
    }

    private String temporalChangeOccurrences(
            Lang2QueryNodeDTO leaf,
            ValueMode valueMode,
            DataTypes dataType,
            List<Long> schemaNodeIds,
            String rawScopePredicate) {
        String column = getColumnName(dataType);
        String source = temporalRawSource(schemaNodeIds, column, leaf.getTimeFilter(), true);
        source = applyTemporalScope(source, rawScopePredicate);
        String operator = leaf.getOperator().trim().toLowerCase(Locale.ROOT);
        String predicate = changePredicate(operator, dataType, leaf.valueAsString())
                .replace("current_value", "agg_value")
                .replace("previous_value", "previous_value");
        if (valueMode.isWindowed()) {
            return "(SELECT later.patient_id, later.schema_node_id, later.event_time, later.id, "
                    + "(later.id::text || ':' || earlier.id::text) AS occurrence_key, "
                    + "later.visit_id, later.import_schema_group_id, later.agg_value FROM "
                    + source
                    + " later JOIN "
                    + source
                    + " earlier ON earlier.patient_id = later.patient_id"
                    + " AND earlier.schema_node_id = later.schema_node_id"
                    + " AND earlier.event_time < later.event_time"
                    + " AND later.event_time <= earlier.event_time + interval '"
                    + valueMode.interval()
                    + "' WHERE "
                    + predicate.replace("agg_value", "later.agg_value")
                            .replace("previous_value", "earlier.agg_value")
                    + ")";
        }
        return "(SELECT changed.patient_id, changed.schema_node_id, changed.event_time, changed.id, "
                + "changed.occurrence_key, "
                + "changed.visit_id, changed.import_schema_group_id, changed.agg_value FROM "
                + "(SELECT base.*, LAG(agg_value) OVER w AS previous_value FROM "
                + source
                + " base WINDOW w AS (PARTITION BY patient_id, schema_node_id ORDER BY event_time, id))"
                + " changed WHERE changed.previous_value IS NOT NULL AND "
                + predicate
                + ")";
    }

    private String buildTemporalRelationClause(
            String sourceOccurrences, String targetOccurrences, TemporalRelation relation) {
        String relationship = temporalJoinPredicate(relation);
        String counts = "(SELECT source.patient_id, source.occurrence_key, "
                + "COUNT(DISTINCT target.occurrence_key) AS relation_count FROM "
                + sourceOccurrences
                + " source LEFT JOIN "
                + targetOccurrences
                + " target ON target.patient_id = source.patient_id AND "
                + relationship
                + " GROUP BY source.patient_id, source.occurrence_key)";
        List<String> having = new ArrayList<>();
        if (relation.atLeast() > 0) {
            having.add("MAX(relation_count) >= " + relation.atLeast());
        }
        if (relation.atMost() != null) {
            // The cap is universal: no source occurrence may exceed it.
            having.add("MAX(relation_count) <= " + relation.atMost());
        }
        if (having.isEmpty()) {
            having.add("MAX(relation_count) >= 0");
        }
        return "(SELECT patient_id FROM "
                + counts
                + " relation_counts GROUP BY patient_id HAVING "
                + String.join(" AND ", having)
                + ")";
    }

    private String temporalJoinPredicate(TemporalRelation relation) {
        return switch (relation.kind()) {
            case WITHIN -> "target.event_time >= source.event_time - interval '"
                    + relation.interval()
                    + "' AND target.event_time <= source.event_time + interval '"
                    + relation.interval()
                    + "'";
            case FOLLOWING -> "target.event_time > source.event_time"
                    + " AND target.event_time <= source.event_time + interval '"
                    + relation.interval()
                    + "'";
            case SAME_TIME -> "CAST(target.event_time AT TIME ZONE 'UTC' AS date) = "
                    + "CAST(source.event_time AT TIME ZONE 'UTC' AS date)";
            case ANYTIME_AFTER -> "target.event_time > source.event_time";
            case SAME_VISIT -> "source.visit_id IS NOT NULL AND target.visit_id = source.visit_id";
            case SAME_GROUP -> "source.import_schema_group_id IS NOT NULL"
                    + " AND target.import_schema_group_id = source.import_schema_group_id";
        };
    }

    // ---- time_aggregation_method / value_aggregation_method -------------------------------
    //
    // Every row source below yields the same five columns: patient_id, schema_node_id,
    // event_time, id, agg_value. The four shapers that follow (ranked, within-average,
    // within-count, whole-series-average) do not care which source produced those columns,
    // so first/last/within-count work the same way whether the series is the raw column
    // (absolute), consecutive LAG steps (delta), or any-pair steps (delta X).

    private String buildAggregatedClause(
            QueryItemDTO item,
            Lang2QueryNodeDTO node,
            ValueMode valueMode,
            TimeAggregation timeAggregation,
            ValueAggregation valueAggregation) {
        String timeSql = timeSql(node.getTimeFilter());
        List<String> perColumnClauses = new ArrayList<>();
        for (List<SchemaNodeEntity> group : columnsFor(item.getOntologyId(), item.getDataTypeId()).values()) {
            DataTypes dataType = group.getFirst().getDataType().getType();
            List<Long> schemaNodeIds = group.stream().map(SchemaNodeEntity::getId).toList();
            perColumnClauses.add(buildAggregatedColumnClause(
                    item, node, valueMode, timeAggregation, valueAggregation, dataType, schemaNodeIds, timeSql));
        }
        return unionClauses(item.getOntologyId(), perColumnClauses);
    }

    private String buildAggregatedColumnClause(
            QueryItemDTO item,
            Lang2QueryNodeDTO node,
            ValueMode valueMode,
            TimeAggregation timeAggregation,
            ValueAggregation valueAggregation,
            DataTypes dataType,
            List<Long> schemaNodeIds,
            String timeSql) {
        if (valueAggregation == ValueAggregation.AVERAGE) {
            // rejectUnsupported already guarantees value_mode is absolute whenever
            // value_aggregation_method = average.
            return buildAverageColumnClause(item, node, dataType, schemaNodeIds, timeSql, timeAggregation);
        }

        String source;
        String predicate;
        if (valueMode.isAbsolute()) {
            NumericParse requestedNumeric = numericParse(node.getDatatype());
            if (requestedNumeric != null
                    && isNumericComparisonOperators(item.getOperator())
                    && isNumericValueDataType(dataType)) {
                source = numericRowSource(schemaNodeIds, dataType, timeSql, requestedNumeric);
                predicate = deltaOperatorPredicate(item.getOperator()).replace("value_delta", "agg_value");
            } else {
                String columnName = getColumnName(dataType);
                source = rawValueSource(schemaNodeIds, columnName, timeSql);
                predicate = buildOperatorClause(item.getOperator(), dataType, "agg_value");
                if (predicate == null || predicate.isBlank()) {
                    throw new QueryRejectedException(
                            "Operator cannot be evaluated with time_aggregation_method for ontology "
                                    + item.getOntologyId());
                }
            }
        } else {
            if (dataType != DataTypes.INT && dataType != DataTypes.FLOAT
                    && dataType != DataTypes.STRING && dataType != DataTypes.CATEGORICAL) {
                throw new QueryRejectedException(
                        "value_mode delta numeric comparison cannot read datatype " + dataType);
            }
            NumericParse requested = numericParse(node.getDatatype());
            if (requested == null) {
                requested = NumericParse.FLOAT;
            }
            String numericSource = "(" + numericDeltaSource(schemaNodeIds, dataType, timeSql, requested) + ")";
            source = valueMode.isWindowed()
                    ? deltaWindowStepsSource(numericSource, valueMode.interval())
                    : deltaStepsSource(numericSource);
            predicate = deltaOperatorPredicate(item.getOperator()).replace("value_delta", "agg_value");
        }

        if (timeAggregation instanceof TimeAggregation.First) {
            return rankedClause(source, predicate, true);
        }
        if (timeAggregation instanceof TimeAggregation.Last) {
            return rankedClause(source, predicate, false);
        }
        if (timeAggregation instanceof TimeAggregation.WithinCount withinCount) {
            return withinCountClause(source, predicate, withinCount);
        }
        throw new QueryRejectedException("time_aggregation_method is not supported in this combination");
    }

    private String buildAverageColumnClause(
            QueryItemDTO item,
            Lang2QueryNodeDTO node,
            DataTypes dataType,
            List<Long> schemaNodeIds,
            String timeSql,
            TimeAggregation timeAggregation) {
        if (dataType != DataTypes.INT && dataType != DataTypes.FLOAT
                && dataType != DataTypes.STRING && dataType != DataTypes.CATEGORICAL) {
            throw new QueryRejectedException(
                    "value_aggregation_method average cannot read datatype " + dataType);
        }
        NumericParse requested = numericParse(node.getDatatype());
        if (requested == null) {
            requested = NumericParse.FLOAT;
        }
        String source = numericRowSource(schemaNodeIds, dataType, timeSql, requested);
        String deltaStylePredicate = deltaOperatorPredicate(item.getOperator());

        if (timeAggregation instanceof TimeAggregation.WithinAverage within) {
            return withinAverageClause(source, deltaStylePredicate, within.interval());
        }
        if (timeAggregation instanceof TimeAggregation.First) {
            return rankedClause(source, deltaStylePredicate.replace("value_delta", "agg_value"), true);
        }
        if (timeAggregation instanceof TimeAggregation.Last) {
            return rankedClause(source, deltaStylePredicate.replace("value_delta", "agg_value"), false);
        }
        // TimeAggregation.None: average over the whole filtered series, no window.
        return wholeSeriesAverageClause(source, deltaStylePredicate.replace("value_delta", "AVG(s.agg_value)"));
    }

    /** Absolute row source: the raw stored column, one row per visit. */
    private String rawValueSource(List<Long> schemaNodeIds, String columnName, String timeSql) {
        return "(SELECT "
                + PATIENT_ID_NAME
                + ", "
                + SCHEMA_ID_NAME
                + ", "
                + VISIT_TIMESTAMP_NAME
                + " AS event_time, id, "
                + columnName
                + " AS agg_value FROM "
                + TABLE_NAME
                + " WHERE "
                + schemaIdPredicate(schemaNodeIds)
                + " AND "
                + columnName
                + " IS NOT NULL AND "
                + VISIT_TIMESTAMP_NAME
                + " IS NOT NULL"
                + timeSql
                + ")";
    }

    /** Absolute numeric row source for {@code value_aggregation_method = average}; reuses the typed-or-parsed-string value. */
    private String numericRowSource(
            List<Long> schemaNodeIds, DataTypes dataType, String timeSql, NumericParse requested) {
        return "(SELECT "
                + PATIENT_ID_NAME
                + ", "
                + SCHEMA_ID_NAME
                + ", "
                + VISIT_TIMESTAMP_NAME
                + " AS event_time, id, numeric_value AS agg_value FROM ("
                + numericDeltaSource(schemaNodeIds, dataType, timeSql, requested)
                + ") numeric_values)";
    }

    /** Consecutive delta step source: current minus previous, event_time = current visit. */
    private String deltaStepsSource(String numericSource) {
        String steps = "(SELECT "
                + PATIENT_ID_NAME
                + ", "
                + SCHEMA_ID_NAME
                + ", "
                + VISIT_TIMESTAMP_NAME
                + " AS event_time, id, numeric_value - LAG(numeric_value) OVER w AS agg_value FROM "
                + numericSource
                + " parsed WINDOW w AS (PARTITION BY "
                + PATIENT_ID_NAME
                + ", "
                + SCHEMA_ID_NAME
                + " ORDER BY "
                + VISIT_TIMESTAMP_NAME
                + ", id)) steps";
        return "(SELECT * FROM " + steps + " WHERE agg_value IS NOT NULL)";
    }

    /** Any-pair delta X step source: later minus earlier for every pair inside the interval. */
    private String deltaWindowStepsSource(String numericSource, String interval) {
        return "(SELECT later."
                + PATIENT_ID_NAME
                + " AS "
                + PATIENT_ID_NAME
                + ", later."
                + SCHEMA_ID_NAME
                + " AS "
                + SCHEMA_ID_NAME
                + ", later."
                + VISIT_TIMESTAMP_NAME
                + " AS event_time, later.id AS id, (later.numeric_value - earlier.numeric_value) AS agg_value FROM "
                + numericSource
                + " later JOIN "
                + numericSource
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
                + "')";
    }

    /** {@code first} / {@code last}: rank by event_time per patient and schema node, keep rn = 1. */
    private String rankedClause(String source, String predicate, boolean first) {
        String order = first ? "ASC" : "DESC";
        return "(SELECT DISTINCT "
                + PATIENT_ID_NAME
                + " FROM (SELECT ranked_source.*, ROW_NUMBER() OVER (PARTITION BY "
                + PATIENT_ID_NAME
                + ", "
                + SCHEMA_ID_NAME
                + " ORDER BY event_time "
                + order
                + ", id "
                + order
                + ") AS rn FROM "
                + source
                + " ranked_source) ranked WHERE rn = 1 AND "
                + predicate
                + ")";
    }

    /** {@code within X} with {@code value_aggregation_method = average}: sliding-window average. */
    private String withinAverageClause(String source, String deltaStylePredicate, String interval) {
        String aggregatePredicate = deltaStylePredicate.replace("value_delta", "AVG(r.agg_value)");
        return "(SELECT DISTINCT w."
                + PATIENT_ID_NAME
                + " FROM "
                + source
                + " w JOIN "
                + source
                + " r ON r."
                + PATIENT_ID_NAME
                + " = w."
                + PATIENT_ID_NAME
                + " AND r."
                + SCHEMA_ID_NAME
                + " = w."
                + SCHEMA_ID_NAME
                + " AND r.event_time >= w.event_time AND r.event_time < w.event_time + interval '"
                + interval
                + "' GROUP BY w."
                + PATIENT_ID_NAME
                + ", w."
                + SCHEMA_ID_NAME
                + ", w.id HAVING "
                + aggregatePredicate
                + ")";
    }

    /** {@code within X at least/at most Y times}: sliding-window occurrence count of the matching rows. */
    private String withinCountClause(String source, String predicate, TimeAggregation.WithinCount withinCount) {
        String hits = "(SELECT * FROM " + source + " hit_source WHERE " + predicate + ")";
        String windowCounts = "(SELECT w."
                + PATIENT_ID_NAME
                + ", w."
                + SCHEMA_ID_NAME
                + ", w.id, COUNT(*) AS window_count FROM "
                + hits
                + " w JOIN "
                + hits
                + " r ON r."
                + PATIENT_ID_NAME
                + " = w."
                + PATIENT_ID_NAME
                + " AND r."
                + SCHEMA_ID_NAME
                + " = w."
                + SCHEMA_ID_NAME
                + " AND r.event_time >= w.event_time AND r.event_time < w.event_time + interval '"
                + withinCount.interval()
                + "' GROUP BY w."
                + PATIENT_ID_NAME
                + ", w."
                + SCHEMA_ID_NAME
                + ", w.id)";
        if (withinCount.atLeast()) {
            return "(SELECT DISTINCT "
                    + PATIENT_ID_NAME
                    + " FROM "
                    + windowCounts
                    + " counts WHERE window_count >= "
                    + withinCount.times()
                    + ")";
        }
        // At-most is universal: every sliding window must respect the cap.
        return "(SELECT "
                + PATIENT_ID_NAME
                + " FROM "
                + windowCounts
                + " counts GROUP BY "
                + PATIENT_ID_NAME
                + ", "
                + SCHEMA_ID_NAME
                + " HAVING MAX(window_count) <= "
                + withinCount.times()
                + ")";
    }

    /** {@code time_aggregation_method = None} with {@code value_aggregation_method = average}: whole-series average. */
    private String wholeSeriesAverageClause(String source, String aggregatePredicate) {
        return "(SELECT DISTINCT s."
                + PATIENT_ID_NAME
                + " FROM "
                + source
                + " s GROUP BY s."
                + PATIENT_ID_NAME
                + ", s."
                + SCHEMA_ID_NAME
                + " HAVING "
                + aggregatePredicate
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
        return encodeNumericLiteral(value);
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

    private enum TemporalRelationKind {
        WITHIN,
        FOLLOWING,
        SAME_TIME,
        ANYTIME_AFTER,
        SAME_VISIT,
        SAME_GROUP
    }

    private record TemporalRelation(
            TemporalRelationKind kind, String interval, int atLeast, Integer atMost) {
    }

    /**
     * {@code time_aggregation_method}: which rows of the time_filter-selected series are
     * evaluated. {@code WithinAverage} only ever reaches the SQL layer when
     * {@code value_aggregation_method = average} (enforced in {@link #rejectUnsupported}).
     */
    private sealed interface TimeAggregation {
        record None() implements TimeAggregation {}

        record First() implements TimeAggregation {}

        record Last() implements TimeAggregation {}

        record WithinAverage(String interval) implements TimeAggregation {}

        record WithinCount(String interval, boolean atLeast, int times) implements TimeAggregation {}
    }

    /**
     * {@code value_aggregation_method}: whether the operator compares each row, or the
     * average of the selected rows. {@code AVERAGE} is only legal with
     * {@code value_mode = absolute} (enforced in {@link #rejectUnsupported}).
     */
    private enum ValueAggregation {
        NONE, AVERAGE
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
        return buildClause(item, null, null);
    }

    public String buildClause(QueryItemDTO item, Object timeFilter) {
        return buildClause(item, timeFilter, null);
    }

    public String buildClause(QueryItemDTO item, Object timeFilter, String datatypeHint) {
        if (item == null || item.getOntologyId() == null || item.getOntologyId().isBlank()) {
            throw new QueryRejectedException("Query item is missing an ontologyId and cannot be evaluated");
        }
        // Only parse value_string as a number when the leaf explicitly declares a numeric
        // datatype and every operator is a magnitude comparison; otherwise behavior is
        // unchanged from before datatype-hinted parsing existed.
        NumericParse requestedNumeric = numericParse(datatypeHint);
        boolean numericHinted = requestedNumeric != null && isNumericComparisonOperators(item.getOperator());

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
            String operatorClause = numericHinted && isNumericValueDataType(dataType)
                    ? numericOperatorClause(item.getOperator(), dataType, requestedNumeric)
                    : buildOperatorClause(item.getOperator(), dataType, columnName);
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

    /**
     * Encodes an operator value using the schema node's actual type, not the client
     * datatype hint. Numeric and boolean values are parsed and rewritten as literals
     * so they cannot become SQL. Text values are quoted after escaping apostrophes.
     */
    private String encodeValue(DataTypes dataType, String value) {
        if (dataType == null) {
            throw new QueryRejectedException("Operator value is missing a data type");
        }
        return switch (dataType) {
            case INT -> encodeIntegerLiteral(value);
            case FLOAT -> encodeNumericLiteral(value);
            case BOOLEAN -> encodeBooleanLiteral(value);
            case DATE -> "'" + requireIsoDate(value) + "'";
            case DATE_TIME -> "'" + requireIsoDateTime(value) + "'";
            case STRING, CATEGORICAL, FILE -> "'" + sanitizeSqlLiteral(requireTextValue(value)) + "'";
        };
    }

    private String encodeIntegerLiteral(String value) {
        String text = requireTextValue(value).trim();
        try {
            return Long.toString(Long.parseLong(text));
        } catch (NumberFormatException e) {
            throw new QueryRejectedException("Integer comparison requires a whole number");
        }
    }

    private String encodeNumericLiteral(String value) {
        String text = requireTextValue(value).trim();
        if (!NUMERIC_LITERAL.matcher(text).matches()) {
            throw new QueryRejectedException("Numeric comparison requires a number");
        }
        try {
            return new BigDecimal(text).toPlainString();
        } catch (NumberFormatException e) {
            throw new QueryRejectedException("Numeric comparison requires a number");
        }
    }

    private String encodeBooleanLiteral(String value) {
        String text = requireTextValue(value).trim().toLowerCase(Locale.ROOT);
        if ("true".equals(text) || "false".equals(text)) {
            return text;
        }
        throw new QueryRejectedException("Boolean comparison requires true or false");
    }

    private String requireTextValue(String value) {
        if (value == null || value.isBlank()) {
            throw new QueryRejectedException("Operator is missing a value");
        }
        return value;
    }

    private String requireIsoDateTime(Object value) {
        String text = String.valueOf(value).trim();
        try {
            OffsetDateTime.parse(text);
            return text;
        } catch (DateTimeParseException ignored) {
            // Accept a date-only value as midnight UTC for DATE_TIME columns.
        }
        try {
            LocalDate.parse(text);
            return text;
        } catch (DateTimeParseException e) {
            throw new QueryRejectedException("DATE_TIME comparison requires an ISO date or date-time: " + text);
        }
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
