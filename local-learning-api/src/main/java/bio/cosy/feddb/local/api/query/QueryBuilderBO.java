package bio.cosy.feddb.local.api.query;

import bio.cosy.feddb.core.api.datamodler.datatype.DataTypes;
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
import java.util.List;
import java.util.Locale;
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
        if (dto == null || dto.getQuery() == null || dto.getQuery().isEmpty()) {
            return EMPTY_PATIENT_QUERY;
        }

        List<String> clauses = dto.getQuery().stream()
                .map(this::buildClause)
                .filter(Objects::nonNull)
                .filter(clause -> !clause.isBlank())
                .collect(Collectors.toList());
        if (clauses.isEmpty()) {
            return EMPTY_PATIENT_QUERY;
        }
        String joined = String.join(" INTERSECT ", clauses);
        return new StringBuilder()
                .append("SELECT DISTINCT ")
                .append(PATIENT_ID_NAME)
                .append(" FROM (")
                .append(joined)
                .append(") AS ")
                .append(RESULT_ALIAS)
                .toString();
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

        List<String> perSchemaClauses = new ArrayList<>();

        for (SchemaNodeEntity schemaNode : schemaNodes) {
            DataTypeEntity dataType = schemaNode.getDataType();
            String columnName = getColumnName(dataType.getType());
            if (columnName == null) {
                Log.errorf("Unsupported data type for schema: %s", schemaNode.getName());
                continue;
            }

            // Build a SELECT DISTINCT clause per schema
            StringBuilder clause = new StringBuilder();
            clause.append("(SELECT DISTINCT ")
                    .append(PATIENT_ID_NAME)
                    .append(" FROM ")
                    .append(TABLE_NAME)
                    .append(" WHERE ")
                    .append(SCHEMA_ID_NAME)
                    .append(" = ")
                    .append(schemaNode.getId())
                    .append(" AND ");

            String operatorClause = buildOperatorClause(item.getOperator(), dataType.getType(), columnName);
            if (operatorClause == null || operatorClause.isBlank()) {
                continue;
            }
            clause.append(operatorClause);
            clause.append(")");
            perSchemaClauses.add(clause.toString());
        }

        // Combine schemaNodes of one Ontology with OR, then wrap
        if (perSchemaClauses.isEmpty()) {
            throw new QueryRejectedException(
                    "No evaluable clause could be built for ontology " + item.getOntologyId()
                            + "; required AND condition cannot be evaluated on this node");
        }
        return "(" + String.join(" UNION ", perSchemaClauses) + ")";
    }

    public List<SchemaNodeEntity> getSchemaNodesForQueryItem(String ontologyId, String dataTypeId) {
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
