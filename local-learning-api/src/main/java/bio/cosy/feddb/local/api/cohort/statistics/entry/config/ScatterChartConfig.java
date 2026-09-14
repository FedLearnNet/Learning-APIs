package bio.cosy.feddb.local.api.cohort.statistics.entry.config;

import bio.cosy.feddb.local.api.cohort.statistics.entry.CustomStatisticType;

public record ScatterChartConfig(
        String xProperty,
        String yProperty,
        String groupByProperty,
        String sizeProperty,
        Boolean trendline
) implements CustomStatisticConfig {
    @Override
    public CustomStatisticType type() {
        return CustomStatisticType.SCATTER;
    }
}
