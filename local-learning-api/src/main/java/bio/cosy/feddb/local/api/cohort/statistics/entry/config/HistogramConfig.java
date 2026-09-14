package bio.cosy.feddb.local.api.cohort.statistics.entry.config;

import bio.cosy.feddb.local.api.cohort.statistics.entry.CustomStatisticType;

public record HistogramConfig(
        String property,
        Integer bins,
        Double min,
        Double max,
        Boolean cumulative,
        Boolean density
) implements CustomStatisticConfig {
    @Override
    public CustomStatisticType type() {
        return CustomStatisticType.HISTOGRAM;
    }
}
