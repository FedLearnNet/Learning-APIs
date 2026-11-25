package bio.cosy.feddb.core.api.query;

import bio.cosy.feddb.core.api.query.item.IntervalPartDTO;
import bio.cosy.feddb.core.api.query.item.IntervalUnit;
import bio.cosy.feddb.core.api.query.item.QueryItemDTO;
import bio.cosy.feddb.core.api.query.item.TemporalRelationDTO;
import bio.cosy.feddb.core.api.query.item.TimeAggregationMethod;
import bio.cosy.feddb.core.api.query.item.ValueAggregationMethod;
import bio.cosy.feddb.core.api.query.item.ValueMode;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/** Rejects query trees that cannot be built into SQL; global checks on save, every local node before running. */
@ApplicationScoped
public class QueryValidationBO {

    // keeps visit_timestamp +/- interval inside the PostgreSQL timestamp range,
    // an overflow would only fail on matching rows and reveal them
    private static final int MAX_INTERVAL_YEARS = 200;
    private static final long MAX_INTERVAL_MINUTES = MAX_INTERVAL_YEARS * IntervalUnit.YEARS.getMaxMinutes();

    /** True for a missing root or a group without conditions, which matches no patient. */
    public boolean isEmptyQuery(QueryItemDTO root) {
        if (root == null) {
            return true;
        }
        if (!root.isGroup()) {
            return false;
        }
        return root.getConditions() == null || root.getConditions().isEmpty();
    }

    /** Throws QueryRejectedException for the first invalid group, leaf or relation. */
    public void validateTree(QueryItemDTO node) {
        if (node == null) {
            throw new QueryRejectedException("Query node is missing");
        }
        if (!node.isGroup()) {
            validateLeaf(node);
            return;
        }
        if (node.getConditions() == null || node.getConditions().isEmpty()) {
            throw new QueryRejectedException("Query group has no conditions");
        }
        if (hasLeafFields(node)) {
            throw new QueryRejectedException("A query group cannot also be a leaf condition");
        }
        if (node.getTemporalRelations() != null) {
            node.getTemporalRelations().forEach(relation -> validateRelation(node.getConditions(), relation));
        }
        node.getConditions().forEach(this::validateTree);
    }

    private void validateLeaf(QueryItemDTO leaf) {
        QueryOperatorTypes operator = leaf.getOperator();
        if (leaf.getOntologyId() == null || leaf.getOntologyId().isBlank()) {
            throw new QueryRejectedException("Query item is missing an ontologyId and cannot be evaluated");
        }
        if (operator == null) {
            throw new QueryRejectedException("Query item is missing an operator");
        }
        boolean delta = leaf.valueModeOrDefault() == ValueMode.DELTA;
        boolean average = leaf.valueAggregationMethodOrDefault() == ValueAggregationMethod.AVERAGE;
        TimeAggregationMethod timeAggregation = leaf.timeAggregationMethodOrDefault();
        if (operator.isChange() && !delta) {
            throw new QueryRejectedException(operator + " requires valueMode DELTA");
        }
        if (operator.isChange() && (timeAggregation != TimeAggregationMethod.NONE || average)) {
            throw new QueryRejectedException(operator + " does not support timeAggregationMethod / valueAggregationMethod");
        }
        if (average && delta) {
            throw new QueryRejectedException("valueAggregationMethod AVERAGE requires valueMode ABSOLUTE");
        }
        if (timeAggregation == TimeAggregationMethod.WITHIN && !average) {
            throw new QueryRejectedException("timeAggregationMethod WITHIN requires valueAggregationMethod AVERAGE");
        }
        if (timeAggregation.needsCount() && average) {
            throw new QueryRejectedException(timeAggregation + " is not allowed with valueAggregationMethod AVERAGE");
        }
        if (timeAggregation.needsCount() && (leaf.getTimeWindowCount() == null || leaf.getTimeWindowCount() <= 0)) {
            throw new QueryRejectedException("timeWindowCount must be positive");
        }
        if (delta && leaf.hasDeltaInterval()) {
            validateInterval(leaf.getDeltaInterval(), "deltaInterval");
        }
        if (timeAggregation == TimeAggregationMethod.WITHIN || timeAggregation.needsCount()) {
            validateInterval(leaf.getTimeWindow(), "timeWindow");
        }
        validateBounds(leaf.getAtLeast(), leaf.getAtMost());
    }

    private void validateRelation(List<QueryItemDTO> conditions, TemporalRelationDTO relation) {
        if (relation == null || relation.getSourceCondition() == null || relation.getTargetCondition() == null
                || relation.getRelationship() == null) {
            throw new QueryRejectedException("temporalRelations requires sourceCondition, targetCondition and relationship");
        }
        int source = relation.getSourceCondition();
        int target = relation.getTargetCondition();
        if (source < 0 || source >= conditions.size() || target < 0 || target >= conditions.size() || source == target) {
            throw new QueryRejectedException("temporalRelations needs two different conditions of its group");
        }
        for (QueryItemDTO endpoint : List.of(conditions.get(source), conditions.get(target))) {
            if (endpoint == null || endpoint.isGroup() || endpoint.isWholeSeriesAverage()) {
                throw new QueryRejectedException(
                        "temporalRelations endpoints must be leaf conditions with an occurrence time");
            }
        }
        if (relation.getRelationship().needsInterval()) {
            validateInterval(relation.getInterval(), "relation interval");
        }
        validateBounds(relation.getAtLeast(), relation.getAtMost());
    }

    private void validateInterval(List<IntervalPartDTO> parts, String field) {
        if (parts == null || parts.isEmpty()) {
            throw new QueryRejectedException(field + " is missing");
        }
        long totalMinutes = 0;
        for (IntervalPartDTO part : parts) {
            if (part == null || part.getCount() == null || part.getUnit() == null) {
                throw new QueryRejectedException(field + " needs a count and a unit");
            }
            if (part.getCount() <= 0) {
                throw new QueryRejectedException(field + " must be positive");
            }
            totalMinutes += (long) part.getCount() * part.getUnit().getMaxMinutes();
            if (totalMinutes > MAX_INTERVAL_MINUTES) {
                throw new QueryRejectedException(field + " exceeds the maximum of " + MAX_INTERVAL_YEARS + " years");
            }
        }
    }

    private void validateBounds(Integer atLeast, Integer atMost) {
        if ((atLeast != null && atLeast < 0) || (atMost != null && atMost < 0)) {
            throw new QueryRejectedException("atLeast / atMost cannot be negative");
        }
        if (atLeast != null && atMost != null && atLeast > atMost) {
            throw new QueryRejectedException("atLeast cannot exceed atMost");
        }
    }

    private boolean hasLeafFields(QueryItemDTO group) {
        return Stream.of(group.getOntologyId(), group.getOperator(), group.getValueMode(), group.getTimeFilter(),
                        group.getTimeAggregationMethod(), group.getValueAggregationMethod(),
                        group.getAtLeast(), group.getAtMost())
                .anyMatch(Objects::nonNull);
    }
}
