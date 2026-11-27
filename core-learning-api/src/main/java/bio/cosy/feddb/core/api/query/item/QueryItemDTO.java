package bio.cosy.feddb.core.api.query.item;

import bio.cosy.feddb.core.api.query.QueryOperatorTypes;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.List;
import java.util.Objects;

/**
 * One node of a query tree: a group when connectingOperator is set, otherwise a leaf condition.
 * Unset enums mean ABSOLUTE / NONE.
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class QueryItemDTO {

    // group
    private ConnectingOperator connectingOperator;
    private List<QueryItemDTO> conditions;
    private List<TemporalRelationDTO> temporalRelations;

    // leaf
    private String ontologyId;
    private String dataTypeId;
    private QueryOperatorTypes operator;
    private String value;

    private ValueMode valueMode;
    private List<IntervalPartDTO> deltaInterval;
    private TimeFilterDTO timeFilter;
    private TimeAggregationMethod timeAggregationMethod;
    private List<IntervalPartDTO> timeWindow;
    private Integer timeWindowCount;
    private ValueAggregationMethod valueAggregationMethod;

    // bounds on the number of matches
    private Integer atLeast;
    private Integer atMost;

    @JsonIgnore
    public boolean isGroup() {
        return connectingOperator != null;
    }

    public static QueryItemDTO andGroup(final QueryItemDTO... items) {
        return andGroup(items == null ? List.of() : List.of(items));
    }

    public static QueryItemDTO andGroup(final List<QueryItemDTO> items) {
        QueryItemDTO group = new QueryItemDTO();
        group.setConnectingOperator(ConnectingOperator.AND);
        group.setConditions(items == null ? List.of() : List.copyOf(items));
        return group;
    }

    // empty for a missing tree
    public static List<QueryItemDTO> leavesOf(final QueryItemDTO query) {
        return query == null ? List.of() : query.leaves();
    }

    // leaf conditions below this node, ignoring group structure
    public List<QueryItemDTO> leaves() {
        if (!isGroup()) {
            return List.of(this);
        }
        if (conditions == null) {
            return List.of();
        }
        return conditions.stream()
                .filter(Objects::nonNull)
                .flatMap(condition -> condition.leaves().stream())
                .toList();
    }

    public ValueMode valueModeOrDefault() {
        return valueMode == null ? ValueMode.ABSOLUTE : valueMode;
    }

    public TimeAggregationMethod timeAggregationMethodOrDefault() {
        return timeAggregationMethod == null ? TimeAggregationMethod.NONE : timeAggregationMethod;
    }

    public ValueAggregationMethod valueAggregationMethodOrDefault() {
        return valueAggregationMethod == null ? ValueAggregationMethod.NONE : valueAggregationMethod;
    }

    public boolean hasDeltaInterval() {
        return deltaInterval != null && !deltaInterval.isEmpty();
    }

    public boolean hasMatchBounds() {
        return atLeast != null || atMost != null;
    }

    // reads visit time: delta, time aggregation or average
    @JsonIgnore
    public boolean isTimeDependent() {
        return valueModeOrDefault() == ValueMode.DELTA
                || timeAggregationMethodOrDefault() != TimeAggregationMethod.NONE
                || valueAggregationMethodOrDefault() == ValueAggregationMethod.AVERAGE;
    }

    // average without a time window, has no single occurrence time
    @JsonIgnore
    public boolean isWholeSeriesAverage() {
        return valueAggregationMethodOrDefault() == ValueAggregationMethod.AVERAGE
                && timeAggregationMethodOrDefault() == TimeAggregationMethod.NONE;
    }
}
