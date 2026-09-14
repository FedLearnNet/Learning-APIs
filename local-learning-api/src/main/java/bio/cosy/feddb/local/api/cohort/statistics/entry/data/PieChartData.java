package bio.cosy.feddb.local.api.cohort.statistics.entry.data;

import bio.cosy.feddb.local.api.cohort.statistics.entry.CustomStatisticType;

import java.util.List;

public record PieChartData(List<Slice> slices) implements CustomStatisticData {

    public record Slice(String name, double value) {}

    @Override
    public CustomStatisticType type() {
        return CustomStatisticType.PIE;
    }
}
