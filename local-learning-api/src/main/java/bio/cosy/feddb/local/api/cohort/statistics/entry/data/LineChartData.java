package bio.cosy.feddb.local.api.cohort.statistics.entry.data;

import bio.cosy.feddb.local.api.cohort.statistics.entry.CustomStatisticType;

import java.util.List;

public record LineChartData(
        List<String> xValues,
        List<Series> series
) implements CustomStatisticData {

    public record Series(String name, List<Double> values) {}

    @Override
    public CustomStatisticType type() {
        return CustomStatisticType.LINE;
    }
}
