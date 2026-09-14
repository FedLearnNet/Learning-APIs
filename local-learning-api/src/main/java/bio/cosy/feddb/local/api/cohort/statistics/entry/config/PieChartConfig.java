package bio.cosy.feddb.local.api.cohort.statistics.entry.config;

import bio.cosy.feddb.local.api.cohort.statistics.entry.CustomStatisticType;

public record PieChartConfig(
        String property,
        String valueProperty,
        CustomStatisticAggregation aggregation,
        Integer topN,
        Boolean donut,
        Boolean showLabels
) implements CustomStatisticConfig {
    @Override
    public CustomStatisticType type() {
        return CustomStatisticType.PIE;
    }
}
