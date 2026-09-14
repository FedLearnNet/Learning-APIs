package bio.cosy.feddb.local.api.cohort.statistics.entry.config;

import bio.cosy.feddb.local.api.cohort.statistics.entry.CustomStatisticType;

public record BoxplotConfig(
        String property,
        String groupByProperty,
        Boolean showOutliers
) implements CustomStatisticConfig {
    @Override
    public CustomStatisticType type() {
        return CustomStatisticType.BOXPLOT;
    }
}
