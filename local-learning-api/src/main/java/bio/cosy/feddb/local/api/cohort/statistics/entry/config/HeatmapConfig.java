package bio.cosy.feddb.local.api.cohort.statistics.entry.config;

import bio.cosy.feddb.local.api.cohort.statistics.entry.CustomStatisticType;

public record HeatmapConfig(
        String xProperty,
        String yProperty,
        String valueProperty,
        CustomStatisticAggregation aggregation,
        Integer xBins,
        Integer yBins
) implements CustomStatisticConfig {
    @Override
    public CustomStatisticType type() {
        return CustomStatisticType.HEATMAP;
    }
}
