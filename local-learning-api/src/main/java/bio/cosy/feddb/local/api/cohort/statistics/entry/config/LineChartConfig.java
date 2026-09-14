package bio.cosy.feddb.local.api.cohort.statistics.entry.config;

import bio.cosy.feddb.local.api.cohort.statistics.entry.CustomStatisticType;

public record LineChartConfig(
        String xProperty,
        String yProperty,
        CustomStatisticAggregation aggregation,
        CustomStatisticTimeGranularity granularity,
        String groupByProperty,
        Boolean smooth,
        Boolean area
) implements CustomStatisticConfig {
    @Override
    public CustomStatisticType type() {
        return CustomStatisticType.LINE;
    }
}
