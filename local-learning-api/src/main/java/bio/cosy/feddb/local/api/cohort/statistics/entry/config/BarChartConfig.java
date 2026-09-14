package bio.cosy.feddb.local.api.cohort.statistics.entry.config;

import bio.cosy.feddb.local.api.cohort.statistics.entry.CustomStatisticType;

public record BarChartConfig(
        String xProperty,
        String yProperty,
        CustomStatisticAggregation aggregation,
        String groupByProperty,
        Orientation orientation,
        Integer limit,
        SortBy sortBy,
        Boolean stacked
) implements CustomStatisticConfig {

    public enum Orientation {VERTICAL, HORIZONTAL}

    public enum SortBy {VALUE_DESC, VALUE_ASC, LABEL}

    @Override
    public CustomStatisticType type() {
        return CustomStatisticType.BAR;
    }
}
