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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@ApplicationScoped
public class QueryBuilderBO {
    private static final String TABLE_NAME = "patient_data";
    private static final String PATIENT_ID_NAME = "patient_id";
    private static final String SCHEMA_ID_NAME = "schema_node_id";
    private static final String RESULT_ALIAS = "query_result";
    private static final String EMPTY_PATIENT_QUERY =
            "SELECT DISTINCT " + PATIENT_ID_NAME + " FROM " + TABLE_NAME + " WHERE 1 = 0";

    @Inject
    SchemaNodeAO schemaNodeAO;

    /**
     * Builds a SQL to count distinct patient IDs matching all query items via INTERSECT
     */
    public String buildCountDistinctSql(QueryDTO dto) {
        if (dto != null && dto.getLang2Query() != null) {
            return wrapDistinct(buildNode(dto.getLang2Query()));
        }
        if (dto == null || dto.getQuery() == null || dto.getQuery().isEmpty()) {
            return EMPTY_PATIENT_QUERY;
        }

        List<String> clauses = dto.getQuery().stream()
                .map(this::buildClause)
                .filter(Objects::nonNull)
                .filter(clause -> !clause.isBlank())
                .collect(Collectors.toList());
        return wrapDistinct(joinClauses(clauses, " INTERSECT "));
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
        QueryItemDTO item = Lang2QueryDocuments.toQueryItem(node);
        if (item == null || item.getOperator() == null || item.getOperator().isEmpty()) {
            throw new QueryRejectedException("Lang2Query leaf is missing a supported operator");
        }
        return buildClause(item);
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
        if (EMPTY_PATIENT_QUERY.equals(joined)) {
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
        List<String> values = new ArrayList<>();
        for (Lang2QueryNodeDTO child : group.getConditions()) {
            if (child == null || child.isGroup()) {
                return null;
            }
            rejectUnsupported(child);
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
            values.add(value);
        }
        Lang2QueryNodeDTO leaf = new Lang2QueryNodeDTO();
        leaf.setOntologyId(ontologyId);
        leaf.setDataTypeId(dataTypeId);
        leaf.setOperator("IN");
        leaf.setValue(String.join(",", values));
        return leaf;
    }

    private void rejectUnsupported(Lang2QueryNodeDTO node) {
        if (node.getTemporalRelations() != null && !node.getTemporalRelations().isEmpty()) {
            throw new QueryRejectedException("temporal_relations is not supported yet");
        }
        if (notBlank(node.getValueMode()) && !"absolute".equalsIgnoreCase(node.getValueMode())) {
            throw new QueryRejectedException("value_mode is not supported yet: " + node.getValueMode());
        }
        if (node.getTimeFilter() != null && !isAnytime(node.getTimeFilter())) {
            throw new QueryRejectedException("time_filter is not supported yet");
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
        String operator = node.getOperator();
        if (operator != null) {
            String normalized = operator.trim().toLowerCase(Locale.ROOT);
            if (normalized.equals("has_changed") || normalized.equals("changed_from") || normalized.equals("changed_to")) {
                throw new QueryRejectedException("Operator is not supported yet: " + operator);
            }
        }
    }

    private boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private boolean isAnytime(Object timeFilter) {
        return timeFilter instanceof String text && "anytime".equalsIgnoreCase(text);
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
