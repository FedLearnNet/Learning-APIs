package bio.cosy.feddb.local.api.cohort.statistics.entry.data;

import bio.cosy.feddb.local.api.cohort.statistics.entry.CustomStatisticType;

import java.util.List;

public record BoxplotData(List<Group> groups) implements CustomStatisticData {

    public record Group(
            String label,
            double min,
            double p25,
            double median,
            double p75,
            double max,
            List<Double> outliers
    ) {}

    @Override
    public CustomStatisticType type() {
        return CustomStatisticType.BOXPLOT;
    }
}
