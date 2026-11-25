package bio.cosy.feddb.local.api.query.builder;

import bio.cosy.feddb.core.api.datamodler.datatype.DataTypes;
import bio.cosy.feddb.core.api.query.QueryOperatorTypes;
import bio.cosy.feddb.core.api.query.item.QueryItemDTO;
import bio.cosy.feddb.core.api.query.item.TimeAggregationMethod;
import bio.cosy.feddb.core.api.query.item.ValueAggregationMethod;
import bio.cosy.feddb.core.api.query.item.ValueMode;
import bio.cosy.feddb.core.api.query.QueryRejectedException;
import bio.cosy.feddb.local.api.schema.schemanode.SchemaNodeBO;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Finds the rows matching one condition, including deltas, time and value aggregation. */
@ApplicationScoped
public class QueryOccurrenceBO {

    // subqueries stay unnamed unless a name is referenced, which needs PostgreSQL 16+
    private static final String COLUMNS =
            "patient_id, schema_node_id, visit_timestamp, id, occurrence_key, visit_id, import_schema_group_id";
    private static final String SERIES = "PARTITION BY patient_id, schema_node_id";

    @Inject
    SchemaNodeBO schemaNodeBO;

    @Inject
    QueryPredicateBO predicateBO;

    /** Distinct patient ids of the given occurrences. */
    public String patientsSql(String occurrencesSql) {
        return "(SELECT DISTINCT patient_id FROM " + occurrencesSql + ")";
    }

    /** Rows matching the leaf; scopeSql filters raw rows, requireVisitTimestamp drops rows without one. */
    public String occurrencesSql(
            QueryItemDTO leaf, String scopeSql, boolean requireVisitTimestamp, Collection<Long> cohortIds) {
        boolean timed = requireVisitTimestamp || leaf.isTimeDependent();
        List<String> parts = new ArrayList<>();
        for (Map.Entry<DataTypes, List<Long>> nodes : schemaNodeBO.findSchemaForQuery(leaf, cohortIds).entrySet()) {
            if (QueryValueHelper.isOperatorValidForDataType(leaf.getOperator(), nodes.getKey())) {
                String matched = columnSql(leaf, nodes.getKey(), nodes.getValue(), scopeSql, timed);
                // agg_value types differ between columns and are not needed after matching
                parts.add("(SELECT " + COLUMNS + " FROM " + matched + ")");
            }
        }
        if (parts.isEmpty()) {
            throw new QueryRejectedException("No evaluable clause could be built for ontology " + leaf.getOntologyId()
                    + "; required AND condition cannot be evaluated on this node");
        }
        return "(" + String.join(" UNION ALL ", parts) + ")";
    }

    private String columnSql(
            QueryItemDTO leaf, DataTypes dataType, List<Long> schemaNodeIds, String scopeSql, boolean timed) {
        QueryOperatorTypes operator = leaf.getOperator();
        if (operator.isChange()) {
            String valueSql = predicateBO.columnSql(dataType);
            String rows = scopedSql(rowsSql(leaf, schemaNodeIds, valueSql, operator.readsValue(), timed), scopeSql);
            return changedSql(leaf, dataType, rows);
        }
        if (readsNumber(leaf, dataType)) {
            String valueSql = predicateBO.numericValueSql(dataType);
            String rows = scopedSql(rowsSql(leaf, schemaNodeIds, valueSql, true, timed), scopeSql);
            if (leaf.valueModeOrDefault() == ValueMode.DELTA) {
                rows = deltaSql(leaf, rows);
            }
            return matchedSql(leaf, rows, predicateBO.comparisonSql(leaf, "agg_value"));
        }
        String valueSql = predicateBO.columnSql(dataType);
        String rows = scopedSql(rowsSql(leaf, schemaNodeIds, valueSql, operator.readsValue(), timed), scopeSql);
        return matchedSql(leaf, rows, predicateBO.operatorSql(leaf, dataType, "agg_value"));
    }

    // deltas and averages always read numbers, comparisons when the value is a number
    private boolean readsNumber(QueryItemDTO leaf, DataTypes dataType) {
        if (leaf.valueModeOrDefault() == ValueMode.DELTA) {
            return true;
        }
        if (leaf.valueAggregationMethodOrDefault() == ValueAggregationMethod.AVERAGE) {
            return true;
        }
        if (!leaf.getOperator().isComparison()) {
            return false;
        }
        return QueryValueHelper.isNumber(leaf.getValue()) && QueryValueHelper.isNumericReadable(dataType);
    }

    private String rowsSql(
            QueryItemDTO leaf, List<Long> schemaNodeIds, String valueSql, boolean requireValue, boolean requireVisitTimestamp) {
        String schemaIds = schemaNodeIds.stream().map(String::valueOf).collect(Collectors.joining(", "));
        String where = "schema_node_id IN (" + schemaIds + ")";
        if (requireVisitTimestamp) {
            where += " AND visit_timestamp IS NOT NULL";
        }
        if (requireValue) {
            where += " AND " + valueSql + " IS NOT NULL";
        }
        where += predicateBO.timeFilterSql(leaf.getTimeFilter());
        return "(SELECT patient_id, schema_node_id, visit_timestamp, id, id AS occurrence_key, visit_id, "
                + "import_schema_group_id, " + valueSql + " AS agg_value FROM patient_data WHERE " + where + ")";
    }

    // scoped names the rows for scopeSql, which compares them with other occurrences
    private String scopedSql(String rowsSql, String scopeSql) {
        if (scopeSql == null) {
            return rowsSql;
        }
        return "(SELECT * FROM " + rowsSql + " scoped WHERE " + scopeSql + ")";
    }

    private String changedSql(QueryItemDTO leaf, DataTypes dataType, String rowsSql) {
        String condition = predicateBO.changeSql(leaf, dataType, "agg_value", "previous_value");
        return "(SELECT * FROM " + stepsSql(leaf, rowsSql) + " WHERE " + condition + ")";
    }

    private String deltaSql(QueryItemDTO leaf, String rowsSql) {
        return "(SELECT " + COLUMNS + ", agg_value - previous_value AS agg_value FROM " + stepsSql(leaf, rowsSql) + ")";
    }

    // adds previous_value: the value of the previous row, or with deltaInterval of every earlier row inside it;
    // later and earlier name the two sides of that self join
    private String stepsSql(QueryItemDTO leaf, String rowsSql) {
        if (leaf.hasDeltaInterval()) {
            return "(SELECT later.patient_id, later.schema_node_id, later.visit_timestamp, later.id, "
                    + "(later.id || ':' || earlier.id) AS occurrence_key, later.visit_id, "
                    + "later.import_schema_group_id, later.agg_value, earlier.agg_value AS previous_value FROM "
                    + rowsSql + " later JOIN " + rowsSql + " earlier ON earlier.patient_id = later.patient_id"
                    + " AND earlier.schema_node_id = later.schema_node_id"
                    + " AND earlier.visit_timestamp < later.visit_timestamp"
                    + " AND later.visit_timestamp <= earlier.visit_timestamp + interval '"
                    + QueryValueHelper.intervalSql(leaf.getDeltaInterval()) + "')";
        }
        return "(SELECT * FROM (SELECT *, LAG(agg_value) OVER (" + SERIES + " ORDER BY visit_timestamp, id)"
                + " AS previous_value FROM " + rowsSql + ") WHERE previous_value IS NOT NULL)";
    }

    // window counts count matching rows, so the predicate comes first there and last everywhere else
    private String matchedSql(QueryItemDTO leaf, String rowsSql, String predicate) {
        TimeAggregationMethod timeAggregation = leaf.timeAggregationMethodOrDefault();
        if (timeAggregation == TimeAggregationMethod.WITHIN_AT_LEAST
                || timeAggregation == TimeAggregationMethod.WITHIN_AT_MOST) {
            return windowCountSql(leaf, filteredSql(rowsSql, predicate));
        }
        return filteredSql(aggregatedSql(leaf, rowsSql), predicate);
    }

    // validation guarantees WITHIN comes with AVERAGE and WITHIN_AT_* without it
    private String aggregatedSql(QueryItemDTO leaf, String rowsSql) {
        TimeAggregationMethod timeAggregation = leaf.timeAggregationMethodOrDefault();
        if (timeAggregation == TimeAggregationMethod.FIRST) {
            return rankedSql(rowsSql, "ASC");
        }
        if (timeAggregation == TimeAggregationMethod.LAST) {
            return rankedSql(rowsSql, "DESC");
        }
        if (timeAggregation == TimeAggregationMethod.WITHIN) {
            return windowAverageSql(leaf, rowsSql);
        }
        if (leaf.valueAggregationMethodOrDefault() == ValueAggregationMethod.AVERAGE) {
            return seriesAverageSql(rowsSql);
        }
        return rowsSql;
    }

    private String filteredSql(String rowsSql, String predicate) {
        return "(SELECT * FROM " + rowsSql + " WHERE " + predicate + ")";
    }

    // first or last row of each series; rn is the row number in visit order
    private String rankedSql(String rowsSql, String order) {
        return "(SELECT * FROM (SELECT *, ROW_NUMBER() OVER (" + SERIES + " ORDER BY visit_timestamp " + order
                + ", id " + order + ") AS rn FROM " + rowsSql + ") WHERE rn = 1)";
    }

    // average over the window from each row
    private String windowAverageSql(QueryItemDTO leaf, String rowsSql) {
        return "(SELECT " + COLUMNS + ", AVG(agg_value) OVER " + windowSql(leaf) + " AS agg_value FROM " + rowsSql + ")";
    }

    // one occurrence per patient and schema node
    private String seriesAverageSql(String rowsSql) {
        return "(SELECT patient_id, schema_node_id, MAX(visit_timestamp) AS visit_timestamp, MAX(id) AS id, "
                + "MAX(id) AS occurrence_key, NULL AS visit_id, NULL AS import_schema_group_id, "
                + "AVG(agg_value) AS agg_value FROM " + rowsSql + " GROUP BY patient_id, schema_node_id)";
    }

    // window_count: hits in the window starting at each hit;
    // at least needs one such window, at most needs every window of the series, so max_window_count
    private String windowCountSql(QueryItemDTO leaf, String hitsSql) {
        String counted = "(SELECT *, COUNT(*) OVER " + windowSql(leaf) + " AS window_count FROM " + hitsSql + ")";
        if (leaf.timeAggregationMethodOrDefault() == TimeAggregationMethod.WITHIN_AT_LEAST) {
            return "(SELECT * FROM " + counted + " WHERE window_count >= " + leaf.getTimeWindowCount() + ")";
        }
        return "(SELECT * FROM (SELECT *, MAX(window_count) OVER (" + SERIES + ") AS max_window_count FROM " + counted
                + ") WHERE max_window_count <= " + leaf.getTimeWindowCount() + ")";
    }

    // from each row up to timeWindow later
    private String windowSql(QueryItemDTO leaf) {
        return "(" + SERIES + " ORDER BY visit_timestamp RANGE BETWEEN CURRENT ROW AND interval '"
                + QueryValueHelper.intervalSql(leaf.getTimeWindow()) + "' FOLLOWING)";
    }
}
