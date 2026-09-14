package bio.cosy.feddb.local.api.cohort.statistics.entry.data;

import bio.cosy.feddb.local.api.cohort.statistics.entry.CustomStatisticType;

import java.util.List;

public record ScatterChartData(List<Group> groups) implements CustomStatisticData {

    /**
     * Each point is [x, y] or [x, y, size]; serialised as a flat array to match echarts.
     */
    public record Group(String name, List<List<Double>> points) {}

    @Override
    public CustomStatisticType type() {
        return CustomStatisticType.SCATTER;
    }
}
