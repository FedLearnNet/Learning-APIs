package bio.cosy.feddb.local.api.query.builder;

import bio.cosy.feddb.core.api.query.QueryOperatorTypes;
import bio.cosy.feddb.core.api.query.item.QueryItemDTO;
import bio.cosy.feddb.core.api.query.item.TemporalRelationDTO;
import bio.cosy.feddb.core.api.query.item.TimeAggregationMethod;
import bio.cosy.feddb.core.api.query.item.ValueAggregationMethod;
import bio.cosy.feddb.core.api.query.item.ValueMode;
import bio.cosy.feddb.local.api.query.QueryRejectedException;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

// rejects trees the SQL would silently misread; values and intervals are checked while building
@ApplicationScoped
public class QueryValidationBO {

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
        validateBounds(relation.getAtLeast(), relation.getAtMost());
    }

    private void validateBounds(Integer atLeast, Integer atMost) {
        if (atLeast != null && atLeast < 0 || atMost != null && atMost < 0) {
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
